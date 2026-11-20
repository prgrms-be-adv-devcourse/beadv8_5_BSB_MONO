// 새 대회에서 부하 계정 1개의 신청 → 주문 → 결제가 실제로 되는지 1건만 확인한다
// 실행: LOADTEST_PASSWORD='…' k6 run -e NO=950 -e BUY_RACE=<대회> -e BUY_COURSE=<종목> probe.js
import http from 'k6/http';
import { sleep } from 'k6';

const BASE = 'https://crewrun.duckdns.org';
const NO = Number(__ENV.NO || 950);
const BUY = { raceId: Number(__ENV.BUY_RACE), raceCourseId: Number(__ENV.BUY_COURSE) };
export const options = { vus: 1, iterations: 1 };

export default function () {
	const login = http.post(`${BASE}/api/v1/auth/login`,
		JSON.stringify({ email: `loadtest${NO}@crewrun.test`, password: __ENV.LOADTEST_PASSWORD }),
		{ headers: { 'Content-Type': 'application/json' } });
	console.log(`login ${login.status}`);
	const h = { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${login.json('data.accessToken')}` } };
	const e = http.post(`${BASE}/api/v1/queue`, JSON.stringify(BUY), h);
	console.log(`enter ${e.status} ${String(e.body).slice(0, 160)}`);
	for (let i = 0; i < 20; i++) {
		const s = http.get(`${BASE}/api/v1/queue/races/${BUY.raceId}/racecourses/${BUY.raceCourseId}`, h);
		if (s.status === 200 && s.json('data.canEnter') === true) { break; }
		sleep(3);
	}
	const a = http.get(`${BASE}/api/v1/order/queue/admission?raceId=${BUY.raceId}&raceCourseId=${BUY.raceCourseId}`, h);
	console.log(`admission ${a.status} ${String(a.body).slice(0, 160)}`);
	const p = http.post(`${BASE}/api/v1/order/participations`, JSON.stringify({
		raceCourseId: BUY.raceCourseId, thirdPartyConsent: true,
		participant: {
			name: '부하테스트', birth: '1995-01-01', gender: 'MALE', phone: '010-1234-5678',
			emergencyContactName: '보호자', emergencyContactPhone: '010-8765-4321',
		},
	}), h);
	console.log(`APPLY status=${p.status} body=${String(p.body).slice(0, 220)}`);
	if (p.status !== 201) { return; }
	const o = http.post(`${BASE}/api/v1/order/orders`, JSON.stringify({ participationId: p.json('data.id') }),
		{ headers: { ...h.headers, 'Idempotency-Key': `probe-${NO}-${Date.now()}` } });
	console.log(`ORDER status=${o.status} body=${String(o.body).slice(0, 220)}`);
	if (o.status !== 201 && o.status !== 200) { return; }
	sleep(2);
	const pay = http.post(`${BASE}/api/v1/order/orders/${o.json('data.id')}/payment`, null, h);
	console.log(`PAY status=${pay.status} body=${String(pay.body).slice(0, 220)}`);
}
