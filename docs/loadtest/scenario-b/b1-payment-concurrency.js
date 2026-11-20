// B-1 주문이 보내는 모양 그대로 결제: 동시 N개가 응답을 받자마자 다음 결제를 보낸다 (판정 기준 문서 2장 B-1)
// 주문 서버 한 대는 예치금 결제를 동시 4개까지만 보내므로, 예치금이 받는 결제는 「초당 몇 건」이 아니라 「동시 몇 개」로 정해진다
// 실행: k6 run -e RUN=2 loadtest/scenario-b/b1-payment-concurrency.js  →  ./loadtest/scenario-b/verify.sh 2
// -e STAGES=4,8 (동시 수, 차례로), -e STAGE_SEC=120. 예열은 -e STAGES=2 -e STAGE_SEC=30
// 처리량(초당 결제 수)은 단계별 결제 건수 ÷ 단계 길이로 본다. 판정(P1·P2)은 결과 정리에서 한다
import http from 'k6/http';
import { check } from 'k6';
import { INTERNAL, JSON_HEADERS, PAYMENT_AMOUNT, payerId, refId } from './lib.js';

const STAGE_SEC = Number(__ENV.STAGE_SEC || 120);
const STAGES = (__ENV.STAGES || '4,8').split(',').map(Number);

const scenarios = {};
const thresholds = {};
STAGES.forEach((vus, i) => {
	scenarios[`vus_${vus}`] = {
		executor: 'constant-vus', vus, duration: `${STAGE_SEC}s`, startTime: `${i * STAGE_SEC}s`,
		gracefulStop: '5s', tags: { stage: `vus${vus}` },
	};
	// p99 1초: 주문이 예치금 응답을 2초까지 기다리므로 그 절반 (근거 있음). 실패 1%는 추천값(미확정)
	thresholds[`http_req_duration{stage:vus${vus}}`] = ['p(99)<1000'];
	thresholds[`http_req_failed{stage:vus${vus}}`] = ['rate<0.01'];
	// 늘 통과하는 기준이지만, 이렇게 걸어야 요약 파일에 단계별 결제 건수가 따로 남는다 (처리량 계산용)
	thresholds[`iterations{stage:vus${vus}}`] = ['count>=0'];
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
