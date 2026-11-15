// 시나리오 A (대회 접수 오픈) 부하 테스트. 단계(STAGE)마다 따로 실행한다: smoke → 10pct
// 실행 전에 login.js로 토큰 파일을 만든다 (30분 안에 실행)
// 실행(backend/에서): k6 run -e STAGE=smoke loadtest/scenario-a/scenario-a.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';

// 운영 서버로는 절대 보내지 않는다. 로컬 주소가 아니면 VU가 시작하기 전(초기화 단계)에 멈춘다
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8000';
if (!/^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(BASE_URL)) {
	throw new Error(`로컬 주소가 아니라 멈춥니다: ${BASE_URL}`);
}

// 토큰: tokens[0]이 loadtest1 회원. 모든 VU가 한 벌을 같이 읽는다
const tokens = new SharedArray('tokens', () => JSON.parse(open('./.tokens.json')));
const tokenAgeMinutes = (Date.now() - Number(open('./.tokens-issued-at.txt'))) / 60000;

// 테스트 데이터 (seed/01-open-races.sql): race 1은 기다리는 사람, race 3은 구매하는 사람
const WAIT = { raceId: 1, raceCourseId: 1 };
const BUY = { raceId: 3, raceCourseId: 3 };
const POLL_INTERVAL_SECONDS = 3; // 프론트 대기열 재확인 간격 (ADMISSION_POLL_MS = 3000)
const START_DELAY_SECONDS = 3; // 순서 묻기·구매는 마지막 사람이 상세를 읽고(2초) 줄에 들어간 뒤 시작한다
const BUY_GRACE_SECONDS = 90; // 구매 1번이 50초쯤이라, 도착이 끝난 뒤 마지막 사람이 결제할 때까지 기다린다

// 단계별 숫자 (2단계 계산). 10%는 동시 접속 31,852명의 10%를 첫 1분에 받는다
const STAGES = {
	// buyerFirstNo: 구매에 쓸 첫 회원 번호. 한 사람은 한 종목에 한 번만 신청되므로 단계마다 겹치지 않게 둔다
	smoke: { enterRate: 5, enterSec: 4, pollSec: 60, buyRate: 1, buySec: 30, warmupSec: 0, buyerFirstNo: 5901 },
	'10pct': { enterRate: 53, enterSec: 60, pollSec: 720, buyRate: 4, buySec: 600, warmupSec: 120, buyerFirstNo: 3201 },
};
const STAGE = STAGES[__ENV.STAGE];
if (!STAGE) {
	throw new Error(`STAGE는 ${Object.keys(STAGES).join(' / ')} 중 하나여야 합니다`);
}
// 기다리는 사람 = 대기열에 실제로 들어간 사람. 이들이 3초마다 한 번씩 물으므로 대기열 조회 = 사람 ÷ 3
STAGE.waiters = STAGE.enterRate * STAGE.enterSec;
STAGE.pollRate = Math.round(STAGE.waiters / POLL_INTERVAL_SECONDS);
// 실행할 때 구매 시작 번호를 바꿀 수 있다 (다시 돌릴 때 이미 신청한 회원을 피하려고)
STAGE.buyerFirstNo = Number(__ENV.BUYER_FIRST_NO || STAGE.buyerFirstNo);
if (tokens.length < STAGE.buyerFirstNo - 1 + STAGE.buyRate * STAGE.buySec) {
	throw new Error('토큰 수가 모자랍니다. login.js의 COUNT를 늘리세요');
}

// 이번 단계가 몇 분 도는지: 순서 묻기가 끝나는 시각과 마지막 구매가 끝나는 시각 중 늦은 쪽
const runMinutes = Math.max(
	STAGE.enterSec + START_DELAY_SECONDS + STAGE.pollSec,
	STAGE.enterSec + START_DELAY_SECONDS + STAGE.buySec + BUY_GRACE_SECONDS,
) / 60;
// 토큰은 만든 뒤 30분에 만료된다. 실행이 끝날 때까지 2분 여유를 남기지 못하면 시작하지 않는다
if (tokenAgeMinutes + runMinutes > 28) {
	throw new Error(`토큰을 만든 지 ${Math.floor(tokenAgeMinutes)}분, 이번 실행은 약 ${Math.ceil(runMinutes)}분이라 `
		+ '중간에 토큰이 만료됩니다. login.js를 다시 돌린 뒤 바로 실행하세요');
}

export const options = {
	scenarios: {
		// 대회 상세를 보고 대기열에 들어간다 (기다리는 사람 수만큼, 한 사람당 한 번)
		// 한 번에 2초쯤 걸린다(상세를 읽는 2초). VU가 모자라면 그 사람은 줄에 못 들어가 dropped_iterations로 남는다
		enter: {
			executor: 'constant-arrival-rate', exec: 'enter',
			rate: STAGE.enterRate, timeUnit: '1s', duration: `${STAGE.enterSec}s`,
			preAllocatedVUs: STAGE.enterRate * 3,
		},
		// 기다리는 사람들이 3초마다 내 순서를 묻는다
		// 마지막 사람이 줄에 들어간 뒤 시작한다. 먼저 물으면 "대기열에 없는 회원"(404)이 난다
		poll: {
			executor: 'constant-arrival-rate', exec: 'poll', startTime: `${STAGE.enterSec + START_DELAY_SECONDS}s`,
			rate: STAGE.pollRate, timeUnit: '1s', duration: `${STAGE.pollSec}s`,
			preAllocatedVUs: Math.max(5, Math.ceil(STAGE.pollRate * 0.15)),
		},
		// 다른 대회에서 새 사람이 초당 buyRate명씩: 입장 → 신청 → 주문 → 결제 (대기열 승격 속도와 같다)
		buy: {
			executor: 'constant-arrival-rate', exec: 'buy', startTime: `${STAGE.enterSec + START_DELAY_SECONDS}s`,
			rate: STAGE.buyRate, timeUnit: '1s', duration: `${STAGE.buySec}s`,
			preAllocatedVUs: Math.max(10, STAGE.buyRate * 60),
			gracefulStop: `${BUY_GRACE_SECONDS}s`,
		},
	},
	// 통과 기준. 워밍업(phase:warmup) 요청은 빼고 본다
	thresholds: {
		'http_req_duration{kind:read,phase:main}': ['p(95)<300'],
		'http_req_duration{kind:queue,phase:main}': ['p(95)<200'],
		'http_req_duration{kind:write,phase:main}': ['p(95)<1000'],
		'http_req_failed{kind:read,phase:main}': ['rate<0.01'],
		'http_req_failed{kind:queue,phase:main}': ['rate<0.01'],
		'http_req_failed{kind:write,phase:main}': ['rate<0.01'],
		checks: ['rate>0.99'],
		dropped_iterations: ['count==0'],
	},
};

// 정상 거절도 실패로 세지 않게 "기대하는 상태 코드"를 정해 둔다 (http_req_failed에 반영)
const OK_200 = http.expectedStatuses(200);
const QUEUE_ENTER_OK = http.expectedStatuses(200, 409); // 409: 이미 입장한 회원
const APPLY_OK = http.expectedStatuses(201, 409); // 409: 이미 신청함 등. 코드는 check로 따로 본다
const ORDER_OK = http.expectedStatuses(200, 201, 409); // 409: 마감(ORDER_SOLD_OUT) 등

// 태그: kind는 통과 기준 묶음, name은 주소를 묶는 이름(주소에 주문 번호가 들어가도 한 줄로 모인다)
function params(token, kind, name, responseCallback, extraHeaders = {}) {
	const phase = exec.instance.currentTestRunDuration < STAGE.warmupSec * 1000 ? 'warmup' : 'main';
	return {
		headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}`, ...extraHeaders },
		tags: { kind, name, phase },
		responseCallback,
	};
}

function tokenOf(memberNo) {
	return tokens[memberNo - 1];
}

function code(res) {
	try {
		return res.json('code');
	} catch (e) {
		return null;
	}
}

function queueStatus(token, race) {
	return http.get(`${BASE_URL}/api/v1/queue/races/${race.raceId}/racecourses/${race.raceCourseId}`,
		params(token, 'queue', 'GET /api/v1/queue/races/{raceId}/racecourses/{raceCourseId}', OK_200));
}

export function enter() {
	const token = tokenOf(exec.scenario.iterationInTest + 1);
	const detail = http.get(`${BASE_URL}/api/v1/product/races/${WAIT.raceId}`,
		params(token, 'read', 'GET /api/v1/product/races/{raceId}', OK_200));
	check(detail, { '대회 상세 200': (r) => r.status === 200 });
	sleep(2); // 상세를 읽고 신청 버튼을 누르기까지
	const res = http.post(`${BASE_URL}/api/v1/queue`, JSON.stringify(WAIT),
		params(token, 'queue', 'POST /api/v1/queue', QUEUE_ENTER_OK));
	check(res, { '대기열 입장 (200 또는 이미 입장 409)': (r) => r.status === 200 || r.status === 409 });
}

export function poll() {
	// 기다리는 사람을 돌아가며 고른다. 초당 waiters÷3번이라 한 사람이 3초마다 한 번씩 묻게 된다
	const memberNo = (exec.scenario.iterationInTest % STAGE.waiters) + 1;
	const res = queueStatus(tokenOf(memberNo), WAIT);
	// canEnter:false(아직 차례 아님)도 정상이다
	check(res, { '대기열 상태 200': (r) => r.status === 200 });
}

export function buy() {
	const memberNo = STAGE.buyerFirstNo + exec.scenario.iterationInTest;
	const token = tokenOf(memberNo);

	const enterRes = http.post(`${BASE_URL}/api/v1/queue`, JSON.stringify(BUY),
		params(token, 'queue', 'POST /api/v1/queue', QUEUE_ENTER_OK));
	check(enterRes, { '구매자 대기열 입장': (r) => r.status === 200 || r.status === 409 });

	// 입장될 때까지 3초마다 묻는다 (승격은 5초마다 20명)
	let admittedToQueue = false;
	for (let attempt = 0; attempt < 40 && !admittedToQueue; attempt++) {
		const res = queueStatus(token, BUY);
		admittedToQueue = res.status === 200 && res.json('data.canEnter') === true;
		if (!admittedToQueue) {
			sleep(POLL_INTERVAL_SECONDS);
		}
	}
	if (!check(admittedToQueue, { '2분 안에 입장': (v) => v })) {
		return;
	}

	// 주문 서비스가 입장 시각을 기록한다. 이걸 건너뛰면 주문이 403(ORDER_QUEUE_NOT_ADMITTED)
	const admission = http.get(
		`${BASE_URL}/api/v1/order/queue/admission?raceId=${BUY.raceId}&raceCourseId=${BUY.raceCourseId}`,
		params(token, 'queue', 'GET /api/v1/order/queue/admission', OK_200));
	if (!check(admission, { '입장 허가 확인': (r) => r.status === 200 && r.json('data.admitted') === true })) {
		return;
	}

	sleep(30); // 신청서 작성

	const apply = http.post(`${BASE_URL}/api/v1/order/participations`, JSON.stringify({
		raceCourseId: BUY.raceCourseId,
		thirdPartyConsent: true,
		participant: {
			name: '부하테스트', birth: '1995-01-01', gender: 'MALE', phone: '010-1234-5678',
			emergencyContactName: '보호자', emergencyContactPhone: '010-8765-4321',
		},
	}), params(token, 'write', 'POST /api/v1/order/participations', APPLY_OK));
	if (apply.status === 409) {
		check(apply, { '신청 정상 거절(이미 신청 등)': () => true });
		return;
	}
	if (!check(apply, { '신청됨 201': (r) => r.status === 201 })) {
		return;
	}

	const order = http.post(`${BASE_URL}/api/v1/order/orders`,
		JSON.stringify({ participationId: apply.json('data.id') }),
		params(token, 'write', 'POST /api/v1/order/orders', ORDER_OK, { 'Idempotency-Key': crypto.randomUUID() }));
	if (order.status === 409) {
		check(order, { '주문 정상 거절(마감)': (r) => code(r) === 'ORDER_SOLD_OUT' });
		return;
	}
	if (!check(order, { '주문됨 201': (r) => r.status === 201 || r.status === 200 })) {
		return;
	}

	sleep(10); // 결제 화면 확인

	const pay = http.post(`${BASE_URL}/api/v1/order/orders/${order.json('data.id')}/payment`, null,
		params(token, 'write', 'POST /api/v1/order/orders/{orderId}/payment', OK_200));
	check(pay, { '결제됨 200': (r) => r.status === 200 && r.json('data.status') === 'PAID' });
}
