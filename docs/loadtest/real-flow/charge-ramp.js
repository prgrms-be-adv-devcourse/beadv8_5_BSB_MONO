// 2부: 충전 한계 (운영 예치금 부하 테스트 2026-11-27, CRU-110)
// 게이트웨이를 거쳐 충전 확정을 초당 10 → 20 → 40 → 80건으로 올리고, 충전 1건마다 화면처럼 잔액·내역을 한 번씩 본다
// 운영은 이 동안 cash 결제사를 가짜로 바꿔 둔다 (README 「운영에서 돌리기」 4번). 실제 Toss는 k6의 가짜 결제 키를 거절한다
//
// 충전 남용 방지: 한 회원이 5분에 3번을 넘기면 10분 막힌다(cash ChargeAbuseGuard, 429)
//   그래서 반복 i는 계정 (i % 계정 수) + 1이 (i / 계정 수)번째로 충전하고, 한 계정은 3번까지만 충전한다
//   1부(rush.js)에서 충전한 계정의 5분 창이 비도록 1부가 끝나고 10분 넘게 쉰 뒤 돌린다
// 충전 금액 1만 원(최소). 하루 한도 100만 원에 1부 충전과 합쳐도 닿지 않게 한다
// 실행:
//   LOADTEST_PASSWORD=… k6 run --summary-export results/<태그>-charge.json charge-ramp.js
//   ACCOUNTS=2310(쓸 계정 수), RATES=10,20,40,80(초당 충전), STEP=45(단계 길이 초)
import http from 'k6/http';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';
import { API, PASSWORD, emailOf } from './config.js';

if (!PASSWORD) {
	throw new Error('LOADTEST_PASSWORD 환경 변수가 없습니다');
}

const ACCOUNTS = Number(__ENV.ACCOUNTS || 2310);
const RATES = (__ENV.RATES || '10,20,40,80').split(',').map(Number);
const STEP_SECONDS = Number(__ENV.STEP || 45);
const MAX_CHARGES_PER_ACCOUNT = 3;
const AMOUNT = 10000;
// 정합성 확인(check-charge.sql)이 이 실행의 충전만 고르는 앞머리
const RUN = __ENV.RUN || Date.now().toString(36);
const ORDER_PREFIX = `charge-cr-${RUN}-`;

const totalCharges = RATES.reduce((sum, rate) => sum + rate * STEP_SECONDS, 0);
if (totalCharges > ACCOUNTS * MAX_CHARGES_PER_ACCOUNT) {
	throw new Error(`충전 ${totalCharges}건은 계정 ${ACCOUNTS}개 × ${MAX_CHARGES_PER_ACCOUNT}번을 넘습니다. ACCOUNTS·RATES·STEP을 줄이세요`);
}

const chargeCompleted = new Counter('charge_completed');
// 의도한 거절: 동시 처리 100건을 넘음(CHARGE_BUSY) / 남용 방지 쿨다운. 실패율에서 빼고 따로 센다
const chargeBusy = new Counter('charge_busy');
const chargeCooldown = new Counter('charge_cooldown');
const chargeFailed = new Counter('charge_failed');

export const options = {
	setupTimeout: '20m',
	scenarios: {
		charge: {
			executor: 'ramping-arrival-rate',
			exec: 'charge',
			startRate: RATES[0],
			timeUnit: '1s',
			preAllocatedVUs: 100,
			// 넉넉히 주지 않으면 목표 속도를 못 따라가 dropped_iterations가 생긴다 (main-scenario runbook)
			maxVUs: 600,
			// 단계마다 바로 목표 속도로 올리고(1초) 그대로 유지한다
			stages: RATES.flatMap((rate) => [
				{ target: rate, duration: '1s' },
				{ target: rate, duration: `${STEP_SECONDS - 1}s` },
			]),
		},
	},
	// 판정 기준 (재기 전에 정함): 충전 확정 p95 1초, 조회 p95 300ms, 실패율 1% (429 거절은 expected라 빠진다)
	// 자동멈춤: 실패율 5%가 30초 넘게 이어지거나, 충전 p99가 5초를 넘으면 멈춘다
	thresholds: {
		'http_req_duration{kind:write}': ['p(95)<1000', { threshold: 'p(99)<5000', abortOnFail: true, delayAbortEval: '60s' }],
		'http_req_duration{kind:read}': ['p(95)<300'],
		'http_req_failed{kind:write}': ['rate<0.01'],
		'http_req_failed{kind:read}': ['rate<0.01'],
		http_req_failed: [{ threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: '30s' }],
		charge_completed: ['count>=0'],
		charge_busy: ['count>=0'],
		charge_cooldown: ['count>=0'],
		charge_failed: ['count==0'],
	},
	summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
};

const JSON_HEADERS = { 'Content-Type': 'application/json' };

// 계정 1~ACCOUNTS를 50개씩 한꺼번에 로그인한다 (nginx 로그인 제한 스위치를 끈 뒤에 돌린다)
export function setup() {
	const tokens = [];
	for (let from = 1; from <= ACCOUNTS; from += 50) {
		const nos = [];
		for (let no = from; no < Math.min(from + 50, ACCOUNTS + 1); no++) {
			nos.push(no);
		}
		const res = http.batch(nos.map((no) => ({
			method: 'POST', url: `${API}/auth/login`,
			body: JSON.stringify({ email: emailOf(no), password: PASSWORD }),
			params: { headers: JSON_HEADERS, tags: { kind: 'setup' } },
		})));
		res.forEach((r, i) => {
			if (r.status !== 200) {
				throw new Error(`${emailOf(nos[i])} 로그인 실패 ${r.status} ${r.body}`);
			}
			tokens.push(r.json('data.accessToken'));
		});
	}
	console.log(`로그인 ${tokens.length}명. 충전 주문번호 앞머리 ${ORDER_PREFIX} (check-charge.sql에 넣는다)`);
	return { tokens, prefix: ORDER_PREFIX };
}

export function charge({ tokens, prefix }) {
	const i = exec.scenario.iterationInTest;
	const account = i % tokens.length;
	const nth = Math.floor(i / tokens.length);
	if (nth >= MAX_CHARGES_PER_ACCOUNT) {
		return;
	}
	const headers = { ...JSON_HEADERS, Authorization: `Bearer ${tokens[account]}` };
	const orderId = `${prefix}${account + 1}-${nth}`;

	const res = http.post(`${API}/cash/charges/confirm`,
		JSON.stringify({ paymentKey: `cr-pk-${orderId}`, orderId, amount: AMOUNT }), {
			headers,
			tags: { kind: 'write', name: 'POST /cash/charges/confirm' },
			responseCallback: http.expectedStatuses(200, 429),
		});
	if (res.status === 200) {
		chargeCompleted.add(1);
	} else if (res.status === 429) {
		(code(res) === 'CHARGE_BUSY' ? chargeBusy : chargeCooldown).add(1);
	} else {
		chargeFailed.add(1);
		console.warn(`충전 실패 ${res.status} ${orderId} ${String(res.body).slice(0, 200)}`);
	}

	// 충전을 마치고 돌아온 예치금 화면: 잔액과 내역 첫 쪽
	http.get(`${API}/cash/wallets/balance`, { headers, tags: { kind: 'read', name: 'GET /cash/wallets/balance' } });
	http.get(`${API}/cash/wallets/ledgers?page=0&size=20`,
		{ headers, tags: { kind: 'read', name: 'GET /cash/wallets/ledgers' } });
}

function code(res) {
	try {
		return res.json('code');
	} catch (e) {
		return null;
	}
}
