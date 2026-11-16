// 시나리오 A 테스트 회원 만들기: 로그인(없으면 가입) → 예치금 100,000원 충전. 몇 번을 다시 돌려도 안전하다
// 먼저 02-identities.sh로 본인인증 결과를 넣는다
// 실행: LOADTEST_PASSWORD='…' k6 run loadtest/scenario-a/seed/03-members.js (COUNT, VUS, BASE_URL로 바꿀 수 있다)
import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8000';
if (!/^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(BASE_URL)) {
	throw new Error(`로컬 주소가 아니라 멈춥니다: ${BASE_URL}`);
}
const PASSWORD = __ENV.LOADTEST_PASSWORD;
if (!PASSWORD) {
	throw new Error('LOADTEST_PASSWORD 환경 변수가 없습니다');
}
const COUNT = Number(__ENV.COUNT || 6000);
const CHARGE_AMOUNT = 100000;
const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const options = {
	scenarios: {
		seed: { executor: 'shared-iterations', vus: Number(__ENV.VUS || 20), iterations: COUNT, maxDuration: '30m' },
	},
	thresholds: { checks: ['rate==1'] },
};

export default function () {
	const i = exec.scenario.iterationInTest + 1;
	const email = `loadtest${i}@crewrun.test`;

	// 이미 가입한 회원은 로그인만 한다. 본인인증 결과는 가입에 한 번 쓰이면 지워져서, 다시 가입하면 400이 난다
	let login = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({ email, password: PASSWORD }),
		{ headers: JSON_HEADERS, responseCallback: http.expectedStatuses(200, 401) });
	if (login.status === 401) {
		const join = http.post(`${BASE_URL}/api/v1/auth/join`, JSON.stringify({
			email, password: PASSWORD, nickname: `load${String(i).padStart(5, '0')}`,
			agreedTerms: ['SERVICE', 'PRIVACY'], verificationToken: `loadtest-${i}`,
		}), { headers: JSON_HEADERS });
		check(join, { '가입됨': (r) => r.status === 201 });
		login = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({ email, password: PASSWORD }),
			{ headers: JSON_HEADERS });
	}
	if (!check(login, { '로그인됨': (r) => r.status === 200 })) {
		return;
	}
	const auth = { ...JSON_HEADERS, Authorization: `Bearer ${login.json('data.accessToken')}` };

	for (let attempt = 1; attempt <= 10; attempt++) {
		const charge = http.post(`${BASE_URL}/api/v1/cash/charges/confirm`, JSON.stringify({
			paymentKey: `seed-pk-${i}`, orderId: `seed-charge-${i}`, amount: CHARGE_AMOUNT,
		}), { headers: auth, responseCallback: http.expectedStatuses(200, 404) });
		if (charge.status === 200) {
			check(charge, { '충전됨': () => true });
			return;
		}
		sleep(1);
	}
	check(null, { '충전됨': () => false });
}
