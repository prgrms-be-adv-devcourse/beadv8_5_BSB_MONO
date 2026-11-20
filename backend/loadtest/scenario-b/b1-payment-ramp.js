// B-1 결제 처리량 한계: 초당 결제 수를 단계마다 올려, 어느 단계에서 판정 기준을 넘는지 찾는다
// 모든 결제가 같은 홀딩 지갑 행을 잠그므로 결제가 한 줄로 서는지(처리량 천장) 본다
// 실행(backend/에서): k6 run -e RUN=2 loadtest/scenario-b/b1-payment-ramp.js  →  ./loadtest/scenario-b/verify.sh 2
// 단계 길이: -e STAGE_SEC=120 (기본 120초). 처음 30초(초당 10건)는 워밍업이라 기준에서 뺀다
import http from 'k6/http';
import { check } from 'k6';
import { INTERNAL, JSON_HEADERS, PAYMENT_AMOUNT, payerId, refId } from './lib.js';

const STAGE_SEC = Number(__ENV.STAGE_SEC || 120);
const WARMUP_SEC = 30;
const RATES = (__ENV.RATES || '20,50,100,200,400').split(',').map(Number);

const scenarios = {
	warmup: {
		executor: 'constant-arrival-rate', rate: 10, timeUnit: '1s', duration: `${WARMUP_SEC}s`,
		preAllocatedVUs: 10, maxVUs: 50, tags: { stage: 'warmup' },
	},
};
const thresholds = {};
RATES.forEach((rate, i) => {
	scenarios[`rate_${rate}`] = {
		executor: 'constant-arrival-rate', rate, timeUnit: '1s', duration: `${STAGE_SEC}s`,
		// 응답이 2초(주문의 예치금 호출 제한)까지 늘어도 버틸 만큼 VU를 둔다. 모자라면 dropped_iterations로 보인다
		preAllocatedVUs: Math.min(rate * 2, 1000), maxVUs: Math.min(rate * 4, 2000),
		startTime: `${WARMUP_SEC + i * STAGE_SEC}s`, tags: { stage: String(rate) },
	};
	// 판정 기준은 README 「판정 기준」. p99 1초·p95 2초는 근거 있음, p95 200ms·실패 1%는 추천값(미확정)
	thresholds[`http_req_duration{stage:${rate}}`] = ['p(95)<200', 'p(99)<1000', 'p(95)<2000'];
	thresholds[`http_req_failed{stage:${rate}}`] = ['rate<0.01'];
	// 목표 속도를 못 따라가면(응답이 느려 VU가 모자라면) 버려진 반복으로 보인다. 처리량 천장의 신호
	// 버려진 반복에는 시나리오에 단 태그(stage)가 붙지 않아 시나리오 이름으로 나눈다
	thresholds[`dropped_iterations{scenario:rate_${rate}}`] = ['count==0'];
});

export const options = {
	scenarios,
	thresholds,
	summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export default function () {
	const res = http.post(`${INTERNAL}/payments/${refId()}`,
		JSON.stringify({ memberId: payerId(), amount: PAYMENT_AMOUNT }), JSON_HEADERS);
	check(res, { '결제 200·결제됨': (r) => r.status === 200 && r.json('data.outcome') === 'PAID' });
}
