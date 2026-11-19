// B-2 결제 중 정산 지급 몰림: 결제를 일정하게 보내다가 가운데 1분만 정산 지급을 섞어, 그동안 결제가 얼마나 느려지는지 본다
// 결제와 정산 지급이 같은 홀딩 지갑 행을 잠근다 (정산 배치는 운영에서 04:00에 몰려 온다)
// 실행(backend/에서): k6 run -e RUN=3 loadtest/scenario-b/b2-payment-with-payout.js  →  ./loadtest/scenario-b/verify.sh 3
// 판정(README 「판정 기준」): 정산이 섞인 동안(during) 결제가 p99 1초·p95 2초(근거 있음), p95 200ms(추천값) 안인지,
// 앞뒤(before·after)와 나란히 비교한다
// 결제 초당 PAY_RATE(기본 50), 정산 초당 PAYOUT_RATE(기본 20). 결제 결과는 phase(before·during·after)로 나눠 본다
// PAY_VUS를 주면 결제를 초당 몇 건 대신 동시 PAY_VUS개로 쉬지 않고 부른다 (측정 컨테이너, 판정 기준 문서 2장 B-2)
import http from 'k6/http';
import { check } from 'k6';
import { INTERNAL, JSON_HEADERS, PAYMENT_AMOUNT, payerId, refId, sellerId } from './lib.js';

const PAY_RATE = Number(__ENV.PAY_RATE || 50);
const PAY_VUS = Number(__ENV.PAY_VUS || 0);
const PAYOUT_RATE = Number(__ENV.PAYOUT_RATE || 20);
// 구간 길이는 -e PHASE_SEC=60 (기본 60초, 스모크는 줄여서)
const PHASE_SEC = Number(__ENV.PHASE_SEC || 60);
const WARMUP_SEC = Math.min(30, PHASE_SEC);

function payScenario(phase, startSec) {
	if (PAY_VUS > 0) {
		return {
			executor: 'constant-vus', vus: PAY_VUS, duration: `${PHASE_SEC}s`, startTime: `${startSec}s`,
			gracefulStop: '5s', exec: 'pay', tags: { phase },
		};
	}
	return {
		executor: 'constant-arrival-rate', rate: PAY_RATE, timeUnit: '1s', duration: `${PHASE_SEC}s`,
		preAllocatedVUs: PAY_RATE * 2, maxVUs: PAY_RATE * 8, startTime: `${startSec}s`,
		exec: 'pay', tags: { phase },
	};
}

export const options = {
	scenarios: {
		warmup: Object.assign(payScenario('warmup', 0), { duration: `${WARMUP_SEC}s` }),
		pay_before: payScenario('before', WARMUP_SEC),
		pay_during: payScenario('during', WARMUP_SEC + PHASE_SEC),
		pay_after: payScenario('after', WARMUP_SEC + PHASE_SEC * 2),
		payout_burst: {
			executor: 'constant-arrival-rate', rate: PAYOUT_RATE, timeUnit: '1s', duration: `${PHASE_SEC}s`,
			preAllocatedVUs: PAYOUT_RATE * 2, maxVUs: PAYOUT_RATE * 8, startTime: `${WARMUP_SEC + PHASE_SEC}s`,
			exec: 'payout', tags: { phase: 'payout' },
		},
	},
	thresholds: {
		'http_req_duration{api:pay,phase:before}': ['p(95)<200'],
		'http_req_duration{api:pay,phase:during}': ['p(95)<200', 'p(99)<1000', 'p(95)<2000'],
		'http_req_duration{api:pay,phase:after}': ['p(95)<200'],
		'http_req_failed{api:pay}': ['rate<0.01'],
		'http_req_failed{api:payout}': ['rate<0.01'],
		dropped_iterations: ['count==0'],
		// 늘 통과하는 기준이지만, 이렇게 걸어야 요약 파일에 구간별 결제 건수가 따로 남는다 (처리량 비교용, P3)
		'iterations{phase:before}': ['count>=0'],
		'iterations{phase:during}': ['count>=0'],
		'iterations{phase:after}': ['count>=0'],
	},
	summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export function pay() {
	const res = http.post(`${INTERNAL}/payments/${refId()}`,
		JSON.stringify({ memberId: payerId(), amount: PAYMENT_AMOUNT }),
		Object.assign({ tags: { api: 'pay' } }, JSON_HEADERS));
	check(res, { '결제 200·결제됨': (r) => r.status === 200 && r.json('data.outcome') === 'PAID' });
}

export function payout() {
	const res = http.post(`${INTERNAL}/payouts/${refId()}`,
		JSON.stringify({ sellerId: sellerId(), payoutStage: 'FINAL', settlementAmount: 10000, feeAmount: 1000 }),
		Object.assign({ tags: { api: 'payout' } }, JSON_HEADERS));
	check(res, { '정산 지급 200': (r) => r.status === 200 });
}
