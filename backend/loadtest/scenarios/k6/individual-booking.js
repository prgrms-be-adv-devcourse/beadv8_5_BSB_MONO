// 개인 예매 흐름(write) — funnel 백엔드 흐름 + 엑셀 화면 부수 API + think-time. 초안: 응답 data.* 키는 실측 전 확인.
// ../main-scenario/funnel.js 를 복사해 exec(book)만 화면 부수 호출·think-time으로 바꾼 것.
// 토큰: ../../main-scenario/k6/login-prod.js (.accounts.json, 30분 안). 실행: k6 run -e STAGE=load -e RACE=10 -e RACE_COURSE=20 individual-booking.js
// 부수 조회 끄기(기준선 funnel과 A/B): -e SIDE=off
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
const SIDE = (__ENV.SIDE || 'on') !== 'off'; // 화면 부수 조회(similar·file·balance 등) 포함 여부
const POLL_INTERVAL_SECONDS = 3;
const THINK = Number(__ENV.THINK || 2); // 화면 전환마다 사용자 대기(초)

// 목표 = 예매 시작/s(TPS). 신청·주문·결제는 1회용 → 단계마다 firstNo로 범위 분리
const STAGES = {
	smoke: { rate: 1, sec: 30, warmupSec: 0, firstNo: 1 },
	load: { rate: 3, sec: 120, warmupSec: 20, firstNo: 41 },
	stress: { rate: 15, sec: 120, warmupSec: 20, firstNo: 401 },
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
		book: {
			executor: 'constant-arrival-rate', exec: 'book',
			rate: STAGE.rate, timeUnit: '1s', duration: `${STAGE.sec}s`,
			preAllocatedVUs: Math.max(30, STAGE.rate * 80), maxVUs: STAGE.rate * 160, // think-time으로 iteration이 길다(guide G)
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
const APPLY_OK = http.expectedStatuses(201, 409);
const ORDER_OK = http.expectedStatuses(200, 201, 409);
const PAY_OK = http.expectedStatuses(200, 202, 503);

function params(token, kind, name, cb, extra = {}) {
	const phase = exec.instance.currentTestRunDuration < STAGE.warmupSec * 1000 ? 'warmup' : 'main';
	return { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}`, ...extra }, tags: { kind, name, phase }, responseCallback: cb };
}
function code(res) { try { return res.json('code'); } catch (e) { return null; } }
function get(token, path, name, cb = OK) { return http.get(`${BASE_URL}${path}`, params(token, 'read', name, cb)); }

export function book() {
	const memberNo = STAGE.firstNo + exec.scenario.iterationInTest;
	const token = accounts[memberNo - 1].accessToken;

	// 1) 대회 목록 화면
	get(token, `/api/v1/product/races?sort=DEADLINE&page=0&size=20`, 'GET /api/v1/product/races');
	sleep(THINK);

	// 2) 대회 상세 화면 (한 화면에서 함께)
	get(token, `/api/v1/product/races/${RACE.raceId}`, 'GET /api/v1/product/races/{raceId}');
	if (SIDE) { get(token, `/api/v1/product/races/${RACE.raceId}/similar`, 'GET races/{raceId}/similar'); }
	get(token, `/api/v1/order/parties/me/race-status?raceId=${RACE.raceId}`, 'GET race-status');
	sleep(THINK);

	// 3) 바텀시트 "개인으로 신청" — API 없음

	// 4) 대기열 화면: 등록 + 화면 조회 + 폴링 + 입장 확인
	const enter = http.post(`${BASE_URL}/api/v1/queue`, JSON.stringify(RACE), params(token, 'queue', 'POST /api/v1/queue', QUEUE_ENTER_OK));
	check(enter, { '대기열 입장(200/409)': (r) => r.status === 200 || r.status === 409 });
	if (SIDE) {
		get(token, `/api/v1/product/races/${RACE.raceId}`, 'GET races/{raceId} (queue)');
		get(token, `/api/v1/file/files?refType=Race&refId=${RACE.raceId}`, 'GET file Race');
		get(token, `/api/v1/file/files?refType=RaceCourse&refId=${RACE.raceCourseId}`, 'GET file RaceCourse');
	}
	let admitted = false;
	for (let attempt = 0; attempt < 40 && !admitted; attempt++) {
		const res = http.get(`${BASE_URL}/api/v1/queue/races/${RACE.raceId}/racecourses/${RACE.raceCourseId}`,
			params(token, 'queue', 'GET /api/v1/queue/races/{raceId}/racecourses/{raceCourseId}', http.expectedStatuses(200, 404)));
		admitted = res.status === 200 && res.json('data.canEnter') === true;
		if (!admitted) { sleep(POLL_INTERVAL_SECONDS); }
	}
	if (!check(admitted, { '2분 안에 입장': (v) => v })) { return; }
	const admission = get(token, `/api/v1/order/queue/admission?raceId=${RACE.raceId}&raceCourseId=${RACE.raceCourseId}`, 'GET order/queue/admission');
	if (!check(admission, { '입장 허가': (r) => r.status === 200 && r.json('data.admitted') === true })) { return; }
	sleep(THINK);

	// 5) 참가 신청서 화면
	if (SIDE) {
		get(token, `/api/v1/order/participations/latest-participant`, 'GET participations/latest-participant', http.expectedStatuses(200, 204));
		get(token, `/api/v1/product/races/${RACE.raceId}/kit-options`, 'GET races/{raceId}/kit-options');
	}
	const apply = http.post(`${BASE_URL}/api/v1/order/participations`, JSON.stringify({
		raceCourseId: RACE.raceCourseId, thirdPartyConsent: true,
		participant: { name: '부하테스트', birth: '1995-01-01', gender: 'MALE', phone: '010-1234-5678', emergencyContactName: '보호자', emergencyContactPhone: '010-8765-4321' },
	}), params(token, 'write', 'POST /api/v1/order/participations', APPLY_OK));
	if (apply.status === 409) { check(apply, { '신청 정상거절': () => true }); return; }
	if (!check(apply, { '신청 201': (r) => r.status === 201 })) { return; }

	const order = http.post(`${BASE_URL}/api/v1/order/orders`, JSON.stringify({ participationId: apply.json('data.id') }),
		params(token, 'write', 'POST /api/v1/order/orders', ORDER_OK, { 'Idempotency-Key': `book-${memberNo}-${Date.now()}` }));
	if (order.status === 409) { check(order, { '주문 정상거절(매진)': (r) => code(r) === 'ORDER_SOLD_OUT' }); return; }
	if (!check(order, { '주문 201/200': (r) => r.status === 201 || r.status === 200 })) { return; }
	const orderId = order.json('data.id');
	sleep(THINK);

	// 6) 결제 화면: 부수 조회 + 결제
	get(token, `/api/v1/order/orders/${orderId}`, 'GET /api/v1/order/orders/{orderId}');
	if (SIDE) {
		get(token, `/api/v1/cash/wallets/balance`, 'GET /api/v1/cash/wallets/balance');
		get(token, `/api/v1/order/participations/${apply.json('data.id')}`, 'GET participations/{id}');
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

	// 7) 결제 완료 화면
	if (SIDE) {
		get(token, `/api/v1/order/orders/${orderId}`, 'GET orders/{orderId} (complete)');
		get(token, `/api/v1/cash/wallets/balance`, 'GET wallets/balance (complete)');
	}
}
