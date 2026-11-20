// B-5 접수가 열렸을 때 충전 승인 (판정 기준 문서 2장 B-5)
// 충전 승인은 게이트웨이에서 예치금으로 바로 가서 주문의 동시 호출 제한을 거치지 않는다
// 준비: cash를 가짜 PG 승인 지연 1초로 띄우고(CASH_CLIENTS_PG_FAKE_CONFIRM_DELAY=1s), seed-charge.sh 실행
// 실행: k6 run -e RUN=7 -e MODE=flow loadtest/scenario-b/b5-charge.js   (초당 48건, 10분)
//       k6 run -e RUN=8 -e MODE=limit loadtest/scenario-b/b5-charge.js  (1,000명이 같은 순간에 한 번씩)
// 같은 회원은 5분에 3번까지만 충전을 시도할 수 있어(CashChargePolicy) 회원 한 명이 한 번만 충전한다
//   flow 회원 940001 ~ 970000, limit 회원 970001 ~ 971000 (seed-charge.sh)
import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { CASH_URL, RUN } from './lib.js';

const MODE = __ENV.MODE || 'flow';
const RATE = Number(__ENV.RATE || 48);
const DURATION_SEC = Number(__ENV.DURATION_SEC || 600);
const LIMIT_VUS = Number(__ENV.LIMIT_VUS || 1000);
// 기획의 평균 참가비 (docs/ideation/11-market-sizing.md A3)
const AMOUNT = 60000;
const FLOW_FIRST = 940001;
const FLOW_MEMBERS = 30000;
const LIMIT_FIRST = 970001;

const scenarios = MODE === 'limit'
	? { limit: { executor: 'per-vu-iterations', vus: LIMIT_VUS, iterations: 1, maxDuration: '2m' } }
	: {
		flow: {
			// PG 승인 1초면 동시에 처리 중인 요청이 약 48개. PG가 느려져도 버려진 반복으로 보이게 넉넉히 둔다
			executor: 'constant-arrival-rate', rate: RATE, timeUnit: '1s', duration: `${DURATION_SEC}s`,
			preAllocatedVUs: RATE * 3, maxVUs: RATE * 20,
		},
	};

export const options = {
	scenarios,
	// flow 판정(P7): p95 2초(PG 1초 + 1초), 실패 1%는 추천값(미확정). limit은 돈 확인만 판정하고 응답 시간은 기록만 한다
	thresholds: MODE === 'limit' ? {} : {
		http_req_duration: ['p(95)<2000'],
		http_req_failed: ['rate<0.01'],
		dropped_iterations: ['count==0'],
	},
	summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

function memberId() {
	if (MODE === 'limit') {
		return LIMIT_FIRST + exec.vu.idInTest - 1;
	}
	const n = exec.scenario.iterationInTest;
	if (n >= FLOW_MEMBERS) {
		throw new Error(`충전 회원 ${FLOW_MEMBERS}명을 다 썼어요`);
	}
	return FLOW_FIRST + n;
}

export default function () {
	const member = memberId();
	const orderId = `lt-b5-${RUN}-${member}`;
	const res = http.post(`${CASH_URL}/api/v1/cash/charges/confirm`,
		JSON.stringify({ paymentKey: `lt-b5-key-${member}`, orderId, amount: AMOUNT }),
		{ headers: { 'Content-Type': 'application/json', 'X-Member-Id': String(member) } });
	check(res, { '충전 200·완료': (r) => r.status === 200 && r.json('data.outcome') === 'COMPLETED' });
}
