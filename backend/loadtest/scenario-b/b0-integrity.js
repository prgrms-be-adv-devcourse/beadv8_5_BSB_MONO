// B-0 돈 정합성: 동시에 몰려도 돈이 한 번만 움직이는지 본다. 성능 시나리오(B-1~B-3) 전후로 돌린다
// 1) 같은 주문 결제 100개 동시  2) 같은 정산 지급 50개 동시  3) 같은 충전 승인 50개 동시
// 4) 결제→환불과 정산 지급을 1분 동안 섞기
// 실행(backend/에서): k6 run -e RUN=1 loadtest/scenario-b/b0-integrity.js  →  ./loadtest/scenario-b/verify.sh 1
// 판정은 verify.sh가 한다 (응답이 모두 200이어도 돈이 두 번 빠졌을 수 있으므로 DB로 확인)
import http from 'k6/http';
import { check } from 'k6';
import { CASH_URL, INTERNAL, JSON_HEADERS, PAYMENT_AMOUNT, RUN, SAME_REF_ID, payerId, refId, sellerId } from './lib.js';

const SAME_ORDER_MEMBER = 900001;
const SAME_PAYOUT_SELLER = 990001;
// 잔액 0원인 충전 확인용 회원 (sql/01-seed.sql). 실행마다 충전 번호가 달라 잔액 상한에는 닿지 않는다
const SAME_CHARGE_MEMBER = 902001;

export const options = {
	scenarios: {
		same_order: { executor: 'per-vu-iterations', vus: 100, iterations: 1, maxDuration: '30s', exec: 'sameOrder' },
		same_payout: {
			executor: 'per-vu-iterations', vus: 50, iterations: 1, maxDuration: '30s', exec: 'samePayout',
			startTime: '10s',
		},
		same_charge: {
			executor: 'per-vu-iterations', vus: 50, iterations: 1, maxDuration: '30s', exec: 'sameCharge',
			startTime: '15s',
		},
		mixed_pay_refund: {
			executor: 'constant-arrival-rate', rate: 30, timeUnit: '1s', duration: '1m',
			preAllocatedVUs: 50, maxVUs: 200, exec: 'payThenRefund', startTime: '20s',
		},
		mixed_payout: {
			executor: 'constant-arrival-rate', rate: 10, timeUnit: '1s', duration: '1m',
			preAllocatedVUs: 20, maxVUs: 100, exec: 'payout', startTime: '20s',
		},
	},
	thresholds: {
		http_req_failed: ['rate==0'],
		checks: ['rate==1'],
		// 판정 P8(결제 p95 2초)용. 기준을 걸어야 요약 파일에 결제만의 응답 시간이 따로 남는다
		'http_req_duration{api:pay}': ['p(95)<2000'],
	},
};

function pay(orderId, memberId) {
	return http.post(`${INTERNAL}/payments/${orderId}`,
		JSON.stringify({ memberId, amount: PAYMENT_AMOUNT }), Object.assign({ tags: { api: 'pay' } }, JSON_HEADERS));
}

function payoutOnce(payoutId, seller, params) {
	return http.post(`${INTERNAL}/payouts/${payoutId}`,
		JSON.stringify({ sellerId: seller, payoutStage: 'FINAL', settlementAmount: 10000, feeAmount: 1000 }),
		Object.assign({ tags: { api: 'payout' } }, JSON_HEADERS, params));
}

export function sameOrder() {
	const res = pay(SAME_REF_ID, SAME_ORDER_MEMBER);
	check(res, { '같은 주문 결제 200·결제됨': (r) => r.status === 200 && r.json('data.outcome') === 'PAID' });
}

// 첫 요청이 은행 송금을 기다리는 동안 온 같은 정산 요청은 503(결과 모름, 정산이 다시 보냄)을 받을 수 있다.
// 그 뒤에 온 요청은 200(이미 지급됨). 500은 기대하지 않는 응답이다
export function samePayout() {
	const res = payoutOnce(SAME_REF_ID, SAME_PAYOUT_SELLER,
		{ responseCallback: http.expectedStatuses(200, 503) });
	check(res, { '같은 정산 지급 200 또는 503': (r) => r.status === 200 || r.status === 503 });
}

// 먼저 온 요청이 PG 승인을 기다리는 동안 같은 충전 요청은 그 결과를 기다렸다가 같은 결과를 받는다.
// PG 승인이 한 번만 불렸는지는 DB로 본다 (충전 원장이 한 줄)
export function sameCharge() {
	const res = http.post(`${CASH_URL}/api/v1/cash/charges/confirm`,
		JSON.stringify({ paymentKey: `lt-b0-key-${RUN}`, orderId: `lt-b0-${RUN}`, amount: PAYMENT_AMOUNT }),
		{ headers: { 'Content-Type': 'application/json', 'X-Member-Id': String(SAME_CHARGE_MEMBER) }, tags: { api: 'charge' } });
	check(res, { '같은 충전 200·완료': (r) => r.status === 200 && r.json('data.outcome') === 'COMPLETED' });
}

export function payThenRefund() {
	const orderId = refId();
	const memberId = payerId();
	const paid = pay(orderId, memberId);
	if (!check(paid, { '결제 200·결제됨': (r) => r.status === 200 && r.json('data.outcome') === 'PAID' })) {
		return;
	}
	const refunded = http.post(`${INTERNAL}/payments/${orderId}/refund`,
		JSON.stringify({ memberId }), Object.assign({ tags: { api: 'refund' } }, JSON_HEADERS));
	check(refunded, { '환불 200': (r) => r.status === 200 });
}

export function payout() {
	check(payoutOnce(refId(), sellerId()), { '정산 지급 200': (r) => r.status === 200 });
}
