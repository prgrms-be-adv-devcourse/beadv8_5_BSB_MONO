// 충전 몰림 측정: 예치금에 충전 확정(가짜 PG 승인)을 초당 RATE건 보낸다. 결제 흐름과 같은 시간에 따로 돈다 (run.sh charge-N)
// 회원은 CHARGE_FROM부터 한 사람이 한 번만 충전한다 (5분 3회 쿨다운·일일 한도에 걸리지 않게)
import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE = __ENV.CASH_BASE || 'http://cash:8081';
const RATE = Number(__ENV.RATE || 10);
const DURATION = __ENV.DURATION || '2m';
const CHARGE_FROM = Number(__ENV.CHARGE_FROM || 500001);

const chargeMs = new Trend('charge_ms', true);
const chargeFailed = new Rate('charge_failed');

export const options = {
	summaryTrendStats: ['min', 'med', 'avg', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'],
	scenarios: {
		charge: {
			executor: 'constant-arrival-rate', rate: RATE, timeUnit: '1s', duration: DURATION,
			preAllocatedVUs: 50, maxVUs: 600, exec: 'charge',
		},
	},
};

export function charge() {
	const memberId = CHARGE_FROM + exec.scenario.iterationInTest;
	const res = http.post(`${BASE}/api/v1/cash/charges/confirm`,
		JSON.stringify({ paymentKey: `load-pk-${memberId}`, orderId: `load-charge-${memberId}`, amount: 10000 }),
		{ headers: { 'Content-Type': 'application/json', 'X-Member-Id': String(memberId) }, timeout: '60s' });
	chargeMs.add(res.timings.duration);
	const ok = check(res, { 'charge completed': (r) => r.status === 200 && r.json('data.outcome') === 'COMPLETED' });
	chargeFailed.add(!ok);
	if (!ok && __ENV.DEBUG === '1') {
		console.warn(`charge ${res.status} ${res.body}`);
	}
}

export function handleSummary(data) {
	const pick = (name) => (data.metrics[name] ? data.metrics[name].values : null);
	const line = JSON.stringify({ charge_ms: pick('charge_ms'), charge_failed: pick('charge_failed'),
		dropped_iterations: pick('dropped_iterations') });
	return { [__ENV.SUMMARY || 'charge.json']: JSON.stringify(data, null, 1), stdout: `${line}\n` };
}
