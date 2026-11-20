// 예매 오픈 몰림을 실제 흐름대로 잰다: N명이 오픈 직후 한 대회에 몰려 줄을 서고, 차례가 오면 신청 → 주문 → 결제까지 간다
// funnel.js와 다른 점: 대기자와 구매자가 같은 사람이다. 줄은 대기열 서비스가 줄여 준다(운영 설정: 5초마다 20명 = 초당 4명)
//   → 길이는 정하지 않는다. 줄이 다 빠질 때까지 돈다 (N명이면 약 N/4초 + 마지막 사람의 구매 40초)
//   → 순번 조회는 처음에 가장 많고(N/3건/초) 줄이 빠지면서 줄어든다
// 실행: run-stage.ps1 -Stage r300 -Tag <날짜-커밋> -Script rush.js   (대회는 실행마다 prepare-race.js로 새로 만든다)
import http from 'k6/http';
import exec from 'k6/execution';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';

const BASE_URL = __ENV.BASE_URL || 'https://crewrun.duckdns.org';
const accounts = new SharedArray('accounts', () => JSON.parse(open('./.accounts.json')));
const tokenAgeMinutes = (Date.now() - Number(open('./.accounts-issued-at.txt'))) / 60000;

for (const key of ['BUY_RACE', 'BUY_COURSE']) {
	if (!__ENV[key]) {
		throw new Error(`${key} 환경 변수가 없습니다. prepare-race.js로 새 대회를 만든 뒤 run-stage.ps1로 실행하세요`);
	}
}
const RACE = { raceId: Number(__ENV.BUY_RACE), raceCourseId: Number(__ENV.BUY_COURSE) };

// 단계 = 오픈에 몰리는 인원. 1,000번 계정은 판매자라 990명이 상한이다
const STAGES = { r30: 30, r300: 300, r600: 600, r990: 990 };
const PEOPLE = STAGES[__ENV.STAGE];
if (!PEOPLE) {
	throw new Error(`STAGE는 ${Object.keys(STAGES).join(' / ')} 중 하나여야 합니다`);
}
if (accounts.length < PEOPLE) {
	throw new Error(`계정이 모자랍니다: ${PEOPLE} 필요, ${accounts.length} 있음`);
}

const ARRIVE_SECONDS = 30;          // 오픈 뒤 이 시간 안에 모두 도착한다 (가정)
const POLL_INTERVAL_SECONDS = 3;    // 화면의 순번 조회 주기
const ADMIT_PER_SECOND = 4;         // 운영 대기열 설정 queue.schedule (20명 / 5초). 길이 계산에만 쓴다
const FORM_THINK_SECONDS = 30;      // 신청서 작성 (가정)
const PAY_THINK_SECONDS = 10;       // 결제 확인 (가정)
const DRAIN_SECONDS = Math.ceil(PEOPLE / ADMIT_PER_SECOND);
// 줄이 빠지는 시간의 2배 + 구매 시간까지 기다린다. 서버가 느려져 줄이 늦게 빠져도 끝까지 본다
const WAIT_LIMIT_SECONDS = DRAIN_SECONDS * 2 + 120;
const runMinutes = (ARRIVE_SECONDS + DRAIN_SECONDS + FORM_THINK_SECONDS + PAY_THINK_SECONDS + 30) / 60;
if (tokenAgeMinutes + runMinutes > 28) {
	throw new Error(`토큰을 만든 지 ${Math.floor(tokenAgeMinutes)}분, 이번 실행 약 ${Math.ceil(runMinutes)}분이라 `
		+ '중간에 만료됩니다. 로그인을 다시 한 뒤 바로 실행하세요');
}

export const options = {
	scenarios: {
		rush: {
			// 한 사람 = VU 하나가 흐름을 한 번 돈다. 도착 시각은 rush()에서 ARRIVE_SECONDS에 고르게 편다
			// (도착률 실행기는 경계에서 한 건이 더 생겨 버려지고, 서버가 느려도 사람 수는 그대로여야 해서 이 방식을 쓴다)
			executor: 'per-vu-iterations', exec: 'rush', vus: PEOPLE, iterations: 1,
			maxDuration: `${ARRIVE_SECONDS + WAIT_LIMIT_SECONDS + FORM_THINK_SECONDS + PAY_THINK_SECONDS + 60}s`,
		},
	},
	// 통과 기준은 funnel.js와 같다. 자동멈춤: 실패율 5%(30초 뒤부터), 쓰기 p99 5초(2분 뒤부터)
	thresholds: {
		'http_req_duration{kind:read}': ['p(95)<300'],
		'http_req_duration{kind:queue}': ['p(95)<200'],
		'http_req_duration{kind:write}': ['p(95)<1000', { threshold: 'p(99)<5000', abortOnFail: true, delayAbortEval: '2m' }],
		'http_req_failed{kind:read}': ['rate<0.01'],
		'http_req_failed{kind:queue}': ['rate<0.01'],
		'http_req_failed{kind:write}': ['rate<0.01'],
		http_req_failed: [{ threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: '30s' }],
		checks: ['rate>0.99'],
	},
};

const OK_200 = http.expectedStatuses(200);
const QUEUE_ENTER_OK = http.expectedStatuses(200, 409);
// 줄에 없으면 404가 온다. 화면은 이때 다시 줄을 선다
const QUEUE_STATUS_OK = http.expectedStatuses(200, 404);
const APPLY_OK = http.expectedStatuses(201);
const ORDER_OK = http.expectedStatuses(200, 201, 409);
const PAY_OK = http.expectedStatuses(200, 202);

function params(token, kind, name, responseCallback, extraHeaders = {}) {
	return {
		headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}`, ...extraHeaders },
		tags: { kind, name },
		responseCallback,
	};
}

function code(res) {
	try { return res.json('code'); } catch (e) { return null; }
}

function enterQueue(token) {
	return http.post(`${BASE_URL}/api/v1/queue`, JSON.stringify(RACE), params(token, 'queue', 'POST /api/v1/queue', QUEUE_ENTER_OK));
}

export function rush() {
	const memberNo = exec.vu.idInTest;
	const a = accounts[memberNo - 1];
	const token = a && a.accessToken;
	sleep(((memberNo - 1) / PEOPLE) * ARRIVE_SECONDS);

	const detail = http.get(`${BASE_URL}/api/v1/product/races/${RACE.raceId}`,
		params(token, 'read', 'GET /api/v1/product/races/{raceId}', OK_200));
	check(detail, { '대회 상세 200': (r) => r.status === 200 });
	sleep(2);

	const entered = enterQueue(token);
	if (!check(entered, { '대기열 입장(200/409)': (r) => r.status === 200 || r.status === 409 })) { return; }

	let admitted = false;
	const deadline = Date.now() + WAIT_LIMIT_SECONDS * 1000;
	while (!admitted && Date.now() < deadline) {
		const res = http.get(`${BASE_URL}/api/v1/queue/races/${RACE.raceId}/racecourses/${RACE.raceCourseId}`,
			params(token, 'queue', 'GET /api/v1/queue/races/{raceId}/racecourses/{raceCourseId}', QUEUE_STATUS_OK));
		if (res.status === 404) { enterQueue(token); }
		admitted = res.status === 200 && res.json('data.canEnter') === true;
		if (!admitted) { sleep(POLL_INTERVAL_SECONDS); }
	}
	if (!check(admitted, { '줄이 빠져 입장': (v) => v })) { return; }

	const admission = http.get(
		`${BASE_URL}/api/v1/order/queue/admission?raceId=${RACE.raceId}&raceCourseId=${RACE.raceCourseId}`,
		params(token, 'queue', 'GET /api/v1/order/queue/admission', OK_200));
	if (!check(admission, { '입장 허가': (r) => r.status === 200 && r.json('data.admitted') === true })) { return; }

	sleep(FORM_THINK_SECONDS);

	const apply = http.post(`${BASE_URL}/api/v1/order/participations`, JSON.stringify({
		raceCourseId: RACE.raceCourseId,
		thirdPartyConsent: true,
		participant: {
			name: '부하테스트', birth: '1995-01-01', gender: 'MALE', phone: '010-1234-5678',
			emergencyContactName: '보호자', emergencyContactPhone: '010-8765-4321',
		},
	}), params(token, 'write', 'POST /api/v1/order/participations', APPLY_OK));
	if (!check(apply, { '신청 201': (r) => r.status === 201 })) { return; }

	const order = http.post(`${BASE_URL}/api/v1/order/orders`,
		JSON.stringify({ participationId: apply.json('data.id') }),
		params(token, 'write', 'POST /api/v1/order/orders', ORDER_OK, { 'Idempotency-Key': `rush-${memberNo}-${Date.now()}` }));
	if (order.status === 409) { check(order, { '주문 정상거절(매진)': (r) => code(r) === 'ORDER_SOLD_OUT' }); return; }
	if (!check(order, { '주문 201/200': (r) => r.status === 201 || r.status === 200 })) { return; }

	sleep(PAY_THINK_SECONDS);

	const pay = http.post(`${BASE_URL}/api/v1/order/orders/${order.json('data.id')}/payment`, null,
		params(token, 'write', 'POST /api/v1/order/orders/{orderId}/payment', PAY_OK));
	check(pay, {
		'결제 200(PAID)/202(PAYING)': (r) => (r.status === 200 && r.json('data.status') === 'PAID')
			|| (r.status === 202 && r.json('data.status') === 'PAYING'),
	});
}
