// 테스트 회원 N명을 로그인시켜 토큰을 .accounts.json에 저장한다. 서비스별 스크립트가 이 파일을 같이 읽는다
// JOIN=1이면 없는 회원은 가입부터 한다 (먼저 identities.sh로 본인인증 결과를 넣어야 한다)
// Nginx가 /api/v1/auth/ 요청을 IP당 초당 1건으로 막아서 한 명씩 1.1초 간격으로 보낸다. 200명이면 약 4분(가입까지 약 8분)
// 실행(backend/에서): LOADTEST_PASSWORD='…' k6 run -e JOIN=1 loadtest/accounts/login.js
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'https://crewrun.duckdns.org';
const PASSWORD = __ENV.LOADTEST_PASSWORD;
if (!PASSWORD) {
	throw new Error('LOADTEST_PASSWORD 환경 변수가 없습니다');
}
const COUNT = Number(__ENV.COUNT || 200);
const JOIN = __ENV.JOIN === '1';
const AUTH_INTERVAL_SECONDS = 1.1;
const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const options = {
	vus: 1,
	iterations: 1,
	setupTimeout: '20m',
	thresholds: { checks: ['rate==1'] },
};

function login(email) {
	sleep(AUTH_INTERVAL_SECONDS);
	return http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({ email, password: PASSWORD }),
		{ headers: JSON_HEADERS, responseCallback: http.expectedStatuses(200, 401) });
}

function join(i, email) {
	sleep(AUTH_INTERVAL_SECONDS);
	const res = http.post(`${BASE_URL}/api/v1/auth/join`, JSON.stringify({
		email, password: PASSWORD, nickname: `load${String(i).padStart(5, '0')}`,
		agreedTerms: ['SERVICE', 'PRIVACY'], verificationToken: `loadtest-${i}`,
	}), { headers: JSON_HEADERS });
	check(res, { '가입됨': (r) => r.status === 201 });
}

export function setup() {
	const accounts = [];
	for (let i = 1; i <= COUNT; i++) {
		const email = `loadtest${i}@crewrun.test`;
		let res = login(email);
		if (res.status === 401 && JOIN) {
			join(i, email);
			res = login(email);
		}
		const ok = check(res, { '로그인됨': (r) => r.status === 200 });
		// Refresh Token은 쿠키로 온다. member.js의 토큰 재발급 시나리오가 쓴다
		const cookie = res.cookies.refreshToken;
		accounts.push(ok ? { accessToken: res.json('data.accessToken'), refreshToken: cookie ? cookie[0].value : null } : null);
	}
	return { issuedAt: Date.now(), accounts };
}

export default function () {
}

// handleSummary만 파일을 쓸 수 있다. 실패한 회원(null)이 있으면 서비스별 스크립트가 그 자리에서 실패로 센다
export function handleSummary(data) {
	const { issuedAt, accounts } = data.setup_data;
	return {
		'loadtest/accounts/.accounts.json': JSON.stringify(accounts),
		'loadtest/accounts/.accounts-issued-at.txt': String(issuedAt),
		stdout: `토큰 ${accounts.filter(Boolean).length}/${accounts.length}개를 loadtest/accounts/.accounts.json에 저장했습니다\n`,
	};
}
