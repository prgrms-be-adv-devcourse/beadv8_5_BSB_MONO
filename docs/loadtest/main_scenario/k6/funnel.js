// 예매 몰림 funnel 운영 부하 (대기열→입장→신청→주문→결제). 단계(STAGE)마다 따로 실행한다
// 토큰은 login-prod.js로 먼저 만든다(.accounts.json, 30분 안에 실행)
// 실행(이 폴더에서): k6 run -e STAGE=s1 -e BUY_RACE=9 -e BUY_COURSE=16 -e WAIT_RACE=10 -e WAIT_COURSE=20 funnel.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';

// 운영(crewrun.duckdns.org) 또는 로컬만 허용
const BASE_URL = __ENV.BASE_URL || 'https://crewrun.duckdns.org';
if (!/^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$|^https:\/\/crewrun\.duckdns\.org$/.test(BASE_URL)) {
	throw new Error(`허용되지 않은 주소라 멈춥니다: ${BASE_URL}`);
}

// login-prod.js가 만든 계정 파일: [{ accessToken, refreshToken }], 0번이 loadtest1
const accounts = new SharedArray('accounts', () => JSON.parse(open('./.accounts.json')));
const tokenAgeMinutes = (Date.now() - Number(open('./.accounts-issued-at.txt'))) / 60000;

// 대회는 실행마다 prepare-race.js로 새로 만든다. 신청은 대회당 1번이라 대회를 재사용하면 구매가 전부 409(중복)로 끝나
// 주문·결제가 한 건도 실행되지 않는다 (2026-11-19 오전 실행이 그랬다). run-stage.ps1이 race.json을 읽어 넘겨 준다
for (const key of ['WAIT_RACE', 'WAIT_COURSE', 'BUY_RACE', 'BUY_COURSE']) {
	if (!__ENV[key]) {
		throw new Error(`${key} 환경 변수가 없습니다. prepare-race.js로 새 대회를 만든 뒤 run-stage.ps1로 실행하세요`);
	}
}
const WAIT = { raceId: Number(__ENV.WAIT_RACE), raceCourseId: Number(__ENV.WAIT_COURSE) };
const BUY = { raceId: Number(__ENV.BUY_RACE), raceCourseId: Number(__ENV.BUY_COURSE) };
const POLL_INTERVAL_SECONDS = 3;
const START_DELAY_SECONDS = 3;
const BUY_GRACE_SECONDS = 90;

// 1,000 계정 기준(1000번은 판매자). 단계마다 새 대회를 쓰므로 구매자 번호가 겹쳐도 된다
// 번호 범위는 이전 실행과 같은 조건으로 비교하려고 그대로 둔다
const STAGES = {
	smoke: { enterRate: 5, enterSec: 4, pollSec: 60, buyRate: 1, buySec: 30, warmupSec: 0, buyerFirstNo: 1 },
	s1: { enterRate: 10, enterSec: 30, pollSec: 180, buyRate: 2, buySec: 120, warmupSec: 20, buyerFirstNo: 41 },
	s2: { enterRate: 20, enterSec: 30, pollSec: 180, buyRate: 3, buySec: 120, warmupSec: 20, buyerFirstNo: 301 },
	s3: { enterRate: 33, enterSec: 30, pollSec: 180, buyRate: 2, buySec: 120, warmupSec: 20, buyerFirstNo: 661 },
	// 결제(write) 한계 확인용. 구매 15/s로 밀어 ~20/s 붕괴 지점을 본다
	buystress: { enterRate: 20, enterSec: 20, pollSec: 60, buyRate: 15, buySec: 60, warmupSec: 10, buyerFirstNo: 1 },
};
const STAGE = STAGES[__ENV.STAGE];
if (!STAGE) {
	throw new Error(`STAGE는 ${Object.keys(STAGES).join(' / ')} 중 하나여야 합니다`);
}
STAGE.waiters = STAGE.enterRate * STAGE.enterSec;
STAGE.pollRate = Math.round(STAGE.waiters / POLL_INTERVAL_SECONDS);
STAGE.buyerFirstNo = Number(__ENV.BUYER_FIRST_NO || STAGE.buyerFirstNo);
const needed = Math.max(STAGE.waiters, STAGE.buyerFirstNo - 1 + STAGE.buyRate * STAGE.buySec);
if (accounts.length < needed) {
	throw new Error(`계정이 모자랍니다: ${needed} 필요, ${accounts.length} 있음. login-prod.js의 COUNT를 늘리세요`);
}

const runMinutes = Math.max(
	STAGE.enterSec + START_DELAY_SECONDS + STAGE.pollSec,
	STAGE.enterSec + START_DELAY_SECONDS + STAGE.buySec + BUY_GRACE_SECONDS,
) / 60;
if (tokenAgeMinutes + runMinutes > 28) {
	throw new Error(`토큰을 만든 지 ${Math.floor(tokenAgeMinutes)}분, 이번 실행 약 ${Math.ceil(runMinutes)}분이라 `
		+ '중간에 만료됩니다. login-prod.js를 다시 돌린 뒤 바로 실행하세요');
}

export const options = {
	scenarios: {
		enter: {
			executor: 'constant-arrival-rate', exec: 'enter',
			rate: STAGE.enterRate, timeUnit: '1s', duration: `${STAGE.enterSec}s`,
			preAllocatedVUs: STAGE.enterRate * 4, maxVUs: STAGE.enterRate * 12,
		},
		poll: {
			executor: 'constant-arrival-rate', exec: 'poll', startTime: `${STAGE.enterSec + START_DELAY_SECONDS}s`,
			rate: STAGE.pollRate, timeUnit: '1s', duration: `${STAGE.pollSec}s`,
			preAllocatedVUs: Math.max(20, STAGE.pollRate), maxVUs: STAGE.pollRate * 6,
		},
		buy: {
			executor: 'constant-arrival-rate', exec: 'buy', startTime: `${STAGE.enterSec + START_DELAY_SECONDS}s`,
			rate: STAGE.buyRate, timeUnit: '1s', duration: `${STAGE.buySec}s`,
			preAllocatedVUs: Math.max(20, STAGE.buyRate * 60), maxVUs: STAGE.buyRate * 140,
			gracefulStop: `${BUY_GRACE_SECONDS}s`,
		},
	},
	// 통과 기준(워밍업 제외) + 자동멈춤: 실패율 5%, write p99 5초가 2분 지속되면 중단(에러 없이 느려지는 붕괴까지 잡는다)
	thresholds: {
		'http_req_duration{kind:read,phase:main}': ['p(95)<300'],
		'http_req_duration{kind:queue,phase:main}': ['p(95)<200'],
		'http_req_duration{kind:write,phase:main}': ['p(95)<1000'],
		'http_req_failed{kind:read,phase:main}': ['rate<0.01'],
		'http_req_failed{kind:queue,phase:main}': ['rate<0.01'],
		'http_req_failed{kind:write,phase:main}': ['rate<0.01'],
		http_req_failed: [{ threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: '30s' }],
		'http_req_duration{kind:write}': [{ threshold: 'p(99)<5000', abortOnFail: true, delayAbortEval: '2m' }],
		checks: ['rate>0.99'],
		dropped_iterations: ['count==0'],
	},
};

const OK_200 = http.expectedStatuses(200);
const QUEUE_ENTER_OK = http.expectedStatuses(200, 409);
const APPLY_OK = http.expectedStatuses(201);
const ORDER_OK = http.expectedStatuses(200, 201, 409);
const PAY_OK = http.expectedStatuses(200, 202);

function params(token, kind, name, responseCallback, extraHeaders = {}) {
	const phase = exec.instance.currentTestRunDuration < STAGE.warmupSec * 1000 ? 'warmup' : 'main';
	return {
		headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}`, ...extraHeaders },
		tags: { kind, name, phase },
		responseCallback,
	};
}

function tokenOf(memberNo) {
	const a = accounts[memberNo - 1];
	return a && a.accessToken;
}

function code(res) {
	try { return res.json('code'); } catch (e) { return null; }
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
	sleep(2);
	const res = http.post(`${BASE_URL}/api/v1/queue`, JSON.stringify(WAIT),
		params(token, 'queue', 'POST /api/v1/queue', QUEUE_ENTER_OK));
	check(res, { '대기열 입장(200/409)': (r) => r.status === 200 || r.status === 409 });
}

export function poll() {
	const memberNo = (exec.scenario.iterationInTest % STAGE.waiters) + 1;
	const res = queueStatus(tokenOf(memberNo), WAIT);
	check(res, { '대기열 상태 200': (r) => r.status === 200 });
}

export function buy() {
	const memberNo = STAGE.buyerFirstNo + exec.scenario.iterationInTest;
	const token = tokenOf(memberNo);

	const enterRes = http.post(`${BASE_URL}/api/v1/queue`, JSON.stringify(BUY),
		params(token, 'queue', 'POST /api/v1/queue', QUEUE_ENTER_OK));
	check(enterRes, { '구매자 대기열 입장': (r) => r.status === 200 || r.status === 409 });

	let admitted = false;
	for (let attempt = 0; attempt < 40 && !admitted; attempt++) {
		const res = queueStatus(token, BUY);
		admitted = res.status === 200 && res.json('data.canEnter') === true;
		if (!admitted) { sleep(POLL_INTERVAL_SECONDS); }
	}
	if (!check(admitted, { '2분 안에 입장': (v) => v })) { return; }

	const admission = http.get(
		`${BASE_URL}/api/v1/order/queue/admission?raceId=${BUY.raceId}&raceCourseId=${BUY.raceCourseId}`,
		params(token, 'queue', 'GET /api/v1/order/queue/admission', OK_200));
	if (!check(admission, { '입장 허가': (r) => r.status === 200 && r.json('data.admitted') === true })) { return; }

	sleep(30);

	const apply = http.post(`${BASE_URL}/api/v1/order/participations`, JSON.stringify({
		raceCourseId: BUY.raceCourseId,
		thirdPartyConsent: true,
		participant: {
			name: '부하테스트', birth: '1995-01-01', gender: 'MALE', phone: '010-1234-5678',
			emergencyContactName: '보호자', emergencyContactPhone: '010-8765-4321',
		},
	}), params(token, 'write', 'POST /api/v1/order/participations', APPLY_OK));
	if (!check(apply, { '신청 201': (r) => r.status === 201 })) { return; }

	const order = http.post(`${BASE_URL}/api/v1/order/orders`,
		JSON.stringify({ participationId: apply.json('data.id') }),
		params(token, 'write', 'POST /api/v1/order/orders', ORDER_OK, { 'Idempotency-Key': `buy-${memberNo}-${Date.now()}` }));
	if (order.status === 409) { check(order, { '주문 정상거절(매진)': (r) => code(r) === 'ORDER_SOLD_OUT' }); return; }
	if (!check(order, { '주문 201/200': (r) => r.status === 201 || r.status === 200 })) { return; }

	sleep(10);

	const pay = http.post(`${BASE_URL}/api/v1/order/orders/${order.json('data.id')}/payment`, null,
		params(token, 'write', 'POST /api/v1/order/orders/{orderId}/payment', PAY_OK));
	check(pay, {
		'결제 200(PAID)/202(PAYING)': (r) => (r.status === 200 && r.json('data.status') === 'PAID')
			|| (r.status === 202 && r.json('data.status') === 'PAYING'),
	});
}
