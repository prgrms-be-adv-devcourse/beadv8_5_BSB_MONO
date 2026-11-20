// B-6·B-7·B-8 바깥이 느릴 때 (docs/measurements/2026-11-18-예치금-부하-2차-판정기준.md 2장)
// 바깥을 늦추는 일은 run.sh가 한다: B-6은 가짜 PG 승인 4초로 다시 띄우고, B-7·B-8은 toxiproxy로 Redis·MySQL에 지연을 넣는다
// 충전 승인 초당 25 → 50 → 75건(단계마다 STAGE_SEC), 같은 시간 내내 결제 동시 4개 + 잔액 조회 초당 10건
// 충전 20건 중 1건은 0.1초 뒤 같은 충전 번호로 한 번 더 보낸다(api:charge_dup, 판정 X4)
// 회원: seed-charge.sh의 940001 ~ 970000 (가입 24시간 안, 잔액 0원). 세 시나리오가 같은 회원을 돌려 쓰고 충전 번호는 PREFIX·RUN으로 나뉜다
// 실행: run.sh가 부른다 (SCENARIOS에 b6, b7, b8)
import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { setTimeout } from 'k6/timers';
import { CASH_URL, INTERNAL, JSON_HEADERS, PAYMENT_AMOUNT, RUN, payerId, refId } from './lib.js';

const PREFIX = __ENV.PREFIX || 'b6';
const STAGE_SEC = Number(__ENV.STAGE_SEC || 180);
const RATES = [25, 50, 75];
// 기획의 평균 참가비 (docs/ideation/11-market-sizing.md A3)
const AMOUNT = 60000;
const FIRST_MEMBER = 940001;
const MEMBERS = 30000;
const DUP_EVERY = 20;
// X4: B-6 PG 지연 4초의 80%
const DUP_MEDIAN_LIMIT_MS = 3200;

// 단계마다 앞 단계들이 쓴 회원 수만큼 건너뛴다
const memberOffsets = RATES.map((_, i) => RATES.slice(0, i).reduce((sum, rate) => sum + rate * STAGE_SEC, 0));
if (memberOffsets[2] + RATES[2] * STAGE_SEC > MEMBERS) {
	throw new Error(`충전 회원 ${MEMBERS}명이 모자라요 (STAGE_SEC=${STAGE_SEC})`);
}

const scenarios = {
	pay: { executor: 'constant-vus', vus: 4, duration: `${STAGE_SEC * 3}s`, exec: 'pay' },
	balance: {
		executor: 'constant-arrival-rate', rate: 10, timeUnit: '1s', duration: `${STAGE_SEC * 3}s`,
		preAllocatedVUs: 10, maxVUs: 200, exec: 'balance',
	},
};
RATES.forEach((rate, i) => {
	// 바깥이 느려 응답이 밀려도 도착 속도는 그대로 둔다. VU가 모자라면 dropped_iterations로 남는다
	scenarios[`charge_${i + 1}`] = {
		executor: 'constant-arrival-rate', rate, timeUnit: '1s', duration: `${STAGE_SEC}s`,
		preAllocatedVUs: rate * 2, maxVUs: rate * 12, exec: 'charge', startTime: `${STAGE_SEC * i}s`,
	};
});

const thresholds = {
	// 기준을 걸어야 요약 파일에 단계·API별 숫자가 따로 남는다. 값은 판정 기준 X1~X4
	'http_req_duration{api:charge_dup}': [`med<${DUP_MEDIAN_LIMIT_MS}`],
};
[1, 2, 3].forEach((stage) => {
	thresholds[`http_req_duration{api:pay,stage:${stage}}`] = ['p(95)<2000', 'p(99)<1000'];
	thresholds[`http_req_duration{api:balance,stage:${stage}}`] = ['p(99)<1000'];
	thresholds[`http_req_duration{api:charge,stage:${stage}}`] = ['p(99)<10000'];
	thresholds[`http_req_failed{api:charge,stage:${stage}}`] = ['rate<0.01'];
});

export const options = {
	scenarios,
	thresholds,
	summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

function stage() {
	const elapsedSec = exec.instance.currentTestRunDuration / 1000;
	return String(Math.min(RATES.length, Math.floor(elapsedSec / STAGE_SEC) + 1));
}

export function pay() {
	const res = http.post(`${INTERNAL}/payments/${refId()}`,
		JSON.stringify({ memberId: payerId(), amount: PAYMENT_AMOUNT }),
		Object.assign({ tags: { api: 'pay', stage: stage() } }, JSON_HEADERS));
	check(res, { '결제 200·결제됨': (r) => r.status === 200 && r.json('data.outcome') === 'PAID' });
}

export function balance() {
	const res = http.get(`${CASH_URL}/api/v1/cash/wallets/balance`,
		{ headers: { 'X-Member-Id': String(payerId()) }, tags: { api: 'balance', stage: stage() } });
	check(res, { '잔액 조회 200': (r) => r.status === 200 });
}

export async function charge() {
	const stageIndex = Number(exec.scenario.name.slice(-1)) - 1;
	const n = exec.scenario.iterationInTest;
	const member = FIRST_MEMBER + memberOffsets[stageIndex] + n;
	const body = JSON.stringify({ paymentKey: `lt-${PREFIX}-key-${member}`, orderId: `lt-${PREFIX}-${RUN}-${member}`, amount: AMOUNT });
	const params = (api) => ({
		headers: { 'Content-Type': 'application/json', 'X-Member-Id': String(member) },
		tags: { api, stage: String(stageIndex + 1) },
	});
	const url = `${CASH_URL}/api/v1/cash/charges/confirm`;
	const first = http.asyncRequest('POST', url, body, params('charge'));
	let second = null;
	if (n % DUP_EVERY === 0) {
		// 첫 요청이 충전을 선점한 뒤에 도착하게 0.1초 띄운다. 두 번째는 토스를 다시 부르지 않고 첫 요청의 결과를 기다린다
		await new Promise((resolve) => setTimeout(resolve, 100));
		second = http.asyncRequest('POST', url, body, params('charge_dup'));
	}
	const done = (r) => r.status === 200 && r.json('data.outcome') === 'COMPLETED';
	check(await first, { '충전 200·완료': done });
	if (second) {
		check(await second, { '같은 충전 두 번째 200·완료': done });
	}
}
