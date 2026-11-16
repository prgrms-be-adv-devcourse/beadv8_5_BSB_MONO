// B-3 환급 카드 장수별 응답 시간: 카드 충전이 1·3·5건인 회원이 전액 환급할 때 응답 시간이 장수에 비례하는지 본다
// 카드 결제취소를 한 건씩 차례로 부르면 응답 시간 ≈ 장수 x PG 지연이 된다
// 준비: cash를 PG 지연 300ms로 띄우고(run-env.sh 위 CASH_CLIENTS_PG_FAKE_CANCEL_DELAY=300ms), seed-withdrawal.sh 실행
// 실행(backend/에서): k6 run loadtest/scenario-b/b3-withdrawal.js
// 환급하면 잔액이 0이 되므로 다시 돌리려면 seed-withdrawal.sh부터 다시 한다
import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { CASH_URL } from './lib.js';

// 장수별 회원 30명을 동시 10명씩 처리한다 (seed-withdrawal.sh의 번호대)
const GROUPS = { 1: 910000, 3: 910100, 5: 910200 };
const MEMBERS = 30;
const VUS = 10;

const scenarios = {};
const thresholds = {};
Object.keys(GROUPS).forEach((cards, i) => {
	scenarios[`cards_${cards}`] = {
		executor: 'shared-iterations', vus: VUS, iterations: MEMBERS, maxDuration: '2m',
		startTime: `${i * 30}s`, exec: 'withdraw', env: { CARDS: cards }, tags: { cards },
	};
	// 판정(README 「판정 기준」): p95 1초·실패 1%는 추천값(미확정). 화면에서 기다리는 요청이라 시나리오 A 쓰기 기준과 같은 값
	thresholds[`http_req_duration{cards:${cards}}`] = ['p(95)<1000'];
	thresholds[`http_req_failed{cards:${cards}}`] = ['rate<0.01'];
});

export const options = { scenarios, thresholds, summaryTrendStats: ['avg', 'med', 'p(95)', 'max'] };

export function withdraw() {
	const memberId = GROUPS[__ENV.CARDS] + 1 + exec.scenario.iterationInTest;
	// 카드로 다 돌려줄 수 있어 계좌 정보는 보내지 않는다
	const res = http.post(`${CASH_URL}/api/v1/cash/withdrawals`, '{}', {
		headers: {
			'Content-Type': 'application/json',
			'X-Member-Id': String(memberId),
			'Idempotency-Key': `lt-b3-${memberId}-${Date.now()}`,
		},
	});
	check(res, { '환급 200': (r) => r.status === 200 });
}
