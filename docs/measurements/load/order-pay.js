// 주문 결제 잠금 부하 측정 (docs/measurements/2026-11-13-결제-잠금-판정기준.md). 측정 환경 밖(운영·dev 서버)에는 돌리지 않는다
// MODE=flow : 종목마다 초당 RATE_PER_COURSE명이 들어와 입장 확인 → 참가 신청 → 주문 → 결제 (S1, S4, S5)
// MODE=burst: 주문 BURST건을 미리 만들고 ROUNDS번 동시에 결제 (S2: 옵션이 모두 다름, SAME_OPTION=1이면 S3)
// 공통: BASE(주문 주소), MEMBER_FROM(이번 실행이 쓸 첫 회원 ID, 실행마다 겹치지 않게), SUMMARY(요약 JSON 경로)
import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE = __ENV.BASE || 'http://order:8084';
const MODE = __ENV.MODE || 'flow';
const MEMBER_FROM = Number(__ENV.MEMBER_FROM || 100001);
const RACE_ID = 900001;
const COURSE_FROM = 900001;
const OPTION_FROM = 900001;
// flow
const COURSES = Number(__ENV.COURSES || 1);
const RATE_PER_COURSE = Number(__ENV.RATE_PER_COURSE || 4);
const DURATION = __ENV.DURATION || '2m';
// S1의 키트 옵션은 앞 5개(티셔츠 사이즈 5개를 흉내) 중 하나. 추천값(미확정)
const FLOW_OPTIONS = Number(__ENV.FLOW_OPTIONS || 5);
// burst
const BURST = Number(__ENV.BURST || 20);
const ROUNDS = Number(__ENV.ROUNDS || 5);
const ROUND_GAP_SECONDS = Number(__ENV.ROUND_GAP_SECONDS || 15);
const SAME_OPTION = __ENV.SAME_OPTION === '1';

const payMs = new Trend('pay_ms', true);
const flowMs = new Trend('flow_ms', true);
const payFailed = new Rate('pay_failed');
const flowFailed = new Rate('flow_failed');
const failures = new Counter('stage_failures');

function burstScenarios() {
	const scenarios = {};
	for (let round = 0; round < ROUNDS; round++) {
		scenarios[`round${round}`] = {
			executor: 'per-vu-iterations', vus: BURST, iterations: 1, maxDuration: '2m',
			startTime: `${round * ROUND_GAP_SECONDS}s`, exec: 'burst', env: { ROUND: String(round) },
		};
	}
	return scenarios;
}

export const options = {
	setupTimeout: '10m',
	summaryTrendStats: ['min', 'med', 'avg', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'],
	scenarios: MODE === 'burst' ? burstScenarios() : {
		flow: {
			executor: 'constant-arrival-rate', rate: RATE_PER_COURSE * COURSES, timeUnit: '1s', duration: DURATION,
			preAllocatedVUs: 50, maxVUs: 400, exec: 'flow',
		},
	},
};

function params(memberId, stage, extraHeaders) {
	return {
		headers: Object.assign({ 'Content-Type': 'application/json', 'X-Member-Id': String(memberId) }, extraHeaders),
		tags: { stage }, timeout: '60s',
	};
}

const participant = {
	name: '부하러너', birth: '1994-06-25', gender: 'MALE', phone: '010-1234-5678',
	emergencyContactName: '가족', emergencyContactPhone: '010-8765-4321', tshirtSize: 'L',
	shippingAddress: { recipientName: '부하러너', recipientPhone: '010-1234-5678', zipcode: '04700', address: '서울 성동구 왕십리로 1' },
};

function fail(stage, res) {
	failures.add(1, { stage, status: String(res.status) });
	if (__ENV.DEBUG === '1') {
		console.warn(`${stage} ${res.status} ${res.body}`);
	}
	return null;
}

// 결제 직전까지: 입장 확인 → 참가 신청 → 주문. 성공하면 주문 ID
function prepare(memberId, courseId) {
	let res = http.get(`${BASE}/api/v1/order/queue/admission?raceId=${RACE_ID}&raceCourseId=${courseId}`,
		params(memberId, 'admission'));
	if (!check(res, { 'admission 200': (r) => r.status === 200 && r.json('data.admitted') === true })) {
		return fail('admission', res);
	}
	res = http.post(`${BASE}/api/v1/order/participations`,
		JSON.stringify({ raceCourseId: courseId, participant, thirdPartyConsent: true }),
		params(memberId, 'participation'));
	if (!check(res, { 'participation 201': (r) => r.status === 201 })) {
		return fail('participation', res);
	}
	res = http.post(`${BASE}/api/v1/order/orders`, JSON.stringify({ participationId: res.json('data.id') }),
		params(memberId, 'order', { 'Idempotency-Key': `load-${memberId}` }));
	if (!check(res, { 'order 201': (r) => r.status === 201 })) {
		return fail('order', res);
	}
	return res.json('data.id');
}

function pay(memberId, orderId, kitOptionId) {
	const res = http.post(`${BASE}/api/v1/order/orders/${orderId}/payment`, JSON.stringify({ kitOptionIds: [kitOptionId] }),
		params(memberId, 'pay'));
	payMs.add(res.timings.duration);
	const ok = check(res, { 'pay 200': (r) => r.status === 200 });
	payFailed.add(!ok);
	if (!ok) {
		fail('pay', res);
	}
	return ok;
}

export function setup() {
	if (MODE !== 'burst') {
		return {};
	}
	const orders = [];
	for (let i = 0; i < BURST * ROUNDS; i++) {
		const memberId = MEMBER_FROM + i;
		const orderId = prepare(memberId, COURSE_FROM + (i % 5));
		if (orderId === null) {
			throw new Error(`준비 실패 member=${memberId}`);
		}
		orders.push({ memberId, orderId });
	}
	return { orders };
}

export function flow() {
	const n = exec.scenario.iterationInTest;
	const memberId = MEMBER_FROM + n;
	const started = Date.now();
	const orderId = prepare(memberId, COURSE_FROM + (n % COURSES));
	const ok = orderId !== null && pay(memberId, orderId, OPTION_FROM + (n % FLOW_OPTIONS));
	flowFailed.add(!ok);
	if (ok) {
		flowMs.add(Date.now() - started);
	}
}

export function burst(data) {
	const i = Number(__ENV.ROUND) * BURST + exec.scenario.iterationInTest;
	const { memberId, orderId } = data.orders[i];
	pay(memberId, orderId, SAME_OPTION ? OPTION_FROM : OPTION_FROM + (i % BURST));
}

export function handleSummary(data) {
	const pick = (name) => (data.metrics[name] ? data.metrics[name].values : null);
	const line = JSON.stringify({ pay_ms: pick('pay_ms'), pay_failed: pick('pay_failed'), flow_failed: pick('flow_failed') });
	return { [__ENV.SUMMARY || 'summary.json']: JSON.stringify(data, null, 1), stdout: `${line}\n` };
}
