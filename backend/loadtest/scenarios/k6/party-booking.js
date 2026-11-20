// 파티 예매 흐름(write) — 파티장 신청(applyLeader) 또는 파티원 결제(payMember). 화면 부수 API + think-time.
// 상태 의존(신청→결제)이라 ROLE로 한 번에 하나만 돌린다. 초안: 응답 data.* 키·좌석 시드는 실측 전 확인.
// 토큰: ../../main-scenario/k6/login-prod.js (.accounts.json, 30분 안).
// 실행(파티장): k6 run -e ROLE=leader -e STAGE=load -e RACE=10 -e RACE_COURSE=20 party-booking.js
// 실행(파티원): k6 run -e ROLE=member -e STAGE=load -e RACE=10 party-booking.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';

const BASE_URL = __ENV.BASE_URL || 'https://crewrun.duckdns.org';
if (!/^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$|^https:\/\/crewrun\.duckdns\.org$/.test(BASE_URL)) {
	throw new Error(`허용되지 않은 주소라 멈춥니다: ${BASE_URL}`);
}

const accounts = new SharedArray('accounts', () => JSON.parse(open('../../main-scenario/k6/.accounts.json')));
const RACE = { raceId: Number(__ENV.RACE || 10), raceCourseId: Number(__ENV.RACE_COURSE || 20) };
const SIDE = (__ENV.SIDE || 'on') !== 'off';
const THINK = Number(__ENV.THINK || 2);
const POLL_INTERVAL_SECONDS = 3;
const ROLE = __ENV.ROLE || 'member'; // leader | member
const EXEC = ROLE === 'leader' ? 'applyLeader' : 'payMember';

const STAGES = {
	smoke: { rate: 1, sec: 30, warmupSec: 0, firstNo: 1 },
	load: { rate: 3, sec: 120, warmupSec: 20, firstNo: 41 },
	stress: { rate: ROLE === 'leader' ? 10 : 15, sec: 120, warmupSec: 20, firstNo: 401 },
};
const STAGE = STAGES[__ENV.STAGE];
if (!STAGE) {
	throw new Error(`STAGE는 ${Object.keys(STAGES).join(' / ')} 중 하나여야 합니다`);
}
STAGE.firstNo = Number(__ENV.FIRST_NO || STAGE.firstNo);
const needed = STAGE.firstNo - 1 + STAGE.rate * STAGE.sec;
if (accounts.length < needed) {
	throw new Error(`계정이 모자랍니다: ${needed} 필요, ${accounts.length} 있음`);
}

export const options = {
	scenarios: {
		party: {
			executor: 'constant-arrival-rate', exec: EXEC,
			rate: STAGE.rate, timeUnit: '1s', duration: `${STAGE.sec}s`,
			preAllocatedVUs: Math.max(30, STAGE.rate * 60), maxVUs: STAGE.rate * 140,
			gracefulStop: '90s',
		},
	},
	thresholds: {
		'http_req_duration{kind:read,phase:main}': ['p(95)<300'],
		'http_req_duration{kind:queue,phase:main}': ['p(95)<200'],
		'http_req_duration{kind:write,phase:main}': ['p(95)<1000'],
		'http_req_failed{kind:write,phase:main}': ['rate<0.01'],
		http_req_failed: [{ threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: '30s' }],
		'http_req_duration{kind:write}': [{ threshold: 'p(99)<5000', abortOnFail: true, delayAbortEval: '2m' }],
		checks: ['rate>0.99'],
		dropped_iterations: ['count==0'],
	},
};

const OK = http.expectedStatuses(200);
const QUEUE_ENTER_OK = http.expectedStatuses(200, 409);
const APPLY_OK = http.expectedStatuses(200, 201, 409);
const ORDER_OK = http.expectedStatuses(200, 201);
const PAY_OK = http.expectedStatuses(200, 202, 503);

function params(token, kind, name, cb, extra = {}) {
	const phase = exec.instance.currentTestRunDuration < STAGE.warmupSec * 1000 ? 'warmup' : 'main';
	return { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}`, ...extra }, tags: { kind, name, phase }, responseCallback: cb };
}
function code(res) { try { return res.json('code'); } catch (e) { return null; } }
function get(token, path, name, cb = OK) { return http.get(`${BASE_URL}${path}`, params(token, 'read', name, cb)); }

// 크루 목록 → 크루 홈 (파티장·파티원 공통 둘러보기). crewId·partyId는 응답에서(초안: 키 확인 필요)
function browseCrew(token) {
	const crews = get(token, `/api/v1/member/crews/me`, 'GET /api/v1/member/crews/me');
	let crewId = null;
	try { crewId = crews.json('data.0.id'); } catch (e) { crewId = null; }
	sleep(THINK);
	if (crewId == null) { return null; }
	get(token, `/api/v1/member/crews/${crewId}`, 'GET /api/v1/member/crews/{crewId}');
	const active = get(token, `/api/v1/order/parties/crews/${crewId}/active`, 'GET parties/crews/{crewId}/active');
	if (SIDE) { get(token, `/api/v1/product/races/${RACE.raceId}`, 'GET races/{raceId} (card)'); }
	let partyId = null;
	try { partyId = active.json('data.0.id'); } catch (e) { partyId = null; }
	sleep(THINK);
	return partyId;
}

export function applyLeader() {
	const token = accounts[STAGE.firstNo - 1 + exec.scenario.iterationInTest].accessToken;
	let partyId = browseCrew(token);
	// 파티 화면
	if (partyId != null) { get(token, `/api/v1/order/parties/${partyId}`, 'GET /api/v1/order/parties/{partyId}'); }
	else {
		const status = get(token, `/api/v1/order/parties/me/race-status?raceId=${RACE.raceId}`, 'GET race-status (fallback)');
		try { partyId = status.json('data.partyId'); } catch (e) { partyId = null; }
	}
	if (!check(partyId, { '파티 id 확보': (v) => v != null })) { return; }
	sleep(THINK);

	// 파티 신청: 대기열 → 폴링 → apply
	const enter = http.post(`${BASE_URL}/api/v1/queue/parties`, JSON.stringify(RACE), params(token, 'queue', 'POST /api/v1/queue/parties', QUEUE_ENTER_OK));
	check(enter, { '파티 대기열 입장(200/409)': (r) => r.status === 200 || r.status === 409 });
	let admitted = false;
	for (let attempt = 0; attempt < 40 && !admitted; attempt++) {
		const res = http.get(`${BASE_URL}/api/v1/queue/parties/races/${RACE.raceId}/racecourses/${RACE.raceCourseId}`,
			params(token, 'queue', 'GET /api/v1/queue/parties/races/{raceId}/racecourses/{raceCourseId}', OK));
		admitted = res.status === 200 && res.json('data.canEnter') === true;
		if (!admitted) { sleep(POLL_INTERVAL_SECONDS); }
	}
	if (!check(admitted, { '2분 안에 입장': (v) => v })) { return; }

	const applied = http.post(`${BASE_URL}/api/v1/order/parties/${partyId}/apply`, null, params(token, 'write', 'POST /api/v1/order/parties/{partyId}/apply', APPLY_OK));
	if (applied.status === 409) { check(applied, { '정상거절(좌석 부족)': (r) => code(r) === 'ORDER_PARTY_CREW_SEAT_SHORTAGE' }); return; }
	if (!check(applied, { '신청 200/201': (r) => r.status === 200 || r.status === 201 })) { return; }
	get(token, `/api/v1/order/parties/${partyId}`, 'GET parties/{partyId} (after apply)');
}

export function payMember() {
	const memberNo = STAGE.firstNo + exec.scenario.iterationInTest;
	const token = accounts[memberNo - 1].accessToken;
	const partyId = browseCrew(token);
	// 파티(결제 진행 중) 화면
	if (partyId != null) { get(token, `/api/v1/order/parties/${partyId}`, 'GET /api/v1/order/parties/{partyId}'); }
	const status = get(token, `/api/v1/order/parties/me/race-status?raceId=${RACE.raceId}`, 'GET race-status');
	if (SIDE) { get(token, `/api/v1/member/members/me`, 'GET /api/v1/member/members/me'); }
	let participationId = null;
	try { participationId = status.json('data.participationId'); } catch (e) { participationId = null; }
	if (!check(participationId, { '신청서 id 확보': (v) => v != null })) { return; }
	sleep(THINK);

	// 주문 (좌석은 파티 때 확보 → 201)
	const order = http.post(`${BASE_URL}/api/v1/order/orders`, JSON.stringify({ participationId }),
		params(token, 'write', 'POST /api/v1/order/orders', ORDER_OK, { 'Idempotency-Key': `party-${memberNo}-${Date.now()}` }));
	if (!check(order, { '주문 200/201': (r) => r.status === 200 || r.status === 201 })) { return; }
	const orderId = order.json('data.id');
	sleep(THINK);

	// 결제 화면
	get(token, `/api/v1/order/orders/${orderId}`, 'GET /api/v1/order/orders/{orderId}');
	if (SIDE) {
		get(token, `/api/v1/cash/wallets/balance`, 'GET /api/v1/cash/wallets/balance');
		get(token, `/api/v1/order/participations/${participationId}`, 'GET participations/{id}');
	}
	let pay = http.post(`${BASE_URL}/api/v1/order/orders/${orderId}/payment`, null, params(token, 'write', 'POST /api/v1/order/orders/{orderId}/payment', PAY_OK));
	for (let i = 0; i < 3 && pay.status === 503; i++) {
		sleep(POLL_INTERVAL_SECONDS);
		pay = http.post(`${BASE_URL}/api/v1/order/orders/${orderId}/payment`, null, params(token, 'write', 'POST payment (retry)', PAY_OK));
	}
	check(pay, {
		'결제 200(PAID)/202(PAYING)': (r) => (r.status === 200 && r.json('data.status') === 'PAID') || (r.status === 202 && r.json('data.status') === 'PAYING'),
	});
	sleep(THINK);
	if (SIDE) { get(token, `/api/v1/order/orders/${orderId}`, 'GET orders/{orderId} (complete)'); }
}
