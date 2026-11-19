// 테스트 회원 N명을 로그인시켜 토큰을 ./.accounts.json 에 저장한다. funnel.js가 같이 읽는다
// 회원은 이미 DB로 만들어 둠(비번 통일). JOIN=1은 보통 불필요(이미 가입). nginx가 /auth를 IP당 초당 1건으로 막아 기본 1.1초 간격
// 실행(이 폴더에서): LOADTEST_PASSWORD='…' COUNT=1000 k6 run login-prod.js
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'https://crewrun.duckdns.org';
const PASSWORD = __ENV.LOADTEST_PASSWORD;
if (!PASSWORD) {
	throw new Error('LOADTEST_PASSWORD 환경 변수가 없습니다');
}
const COUNT = Number(__ENV.COUNT || 1000);
const JOIN = __ENV.JOIN === '1';
// nginx 제한(IP당 초당 1건)이 걸려 있으면 1.1초. 운영 nginx 로그인 제한 스위치를 "true"로 꺼 두었으면 -e AUTH_INTERVAL=0.2 처럼 줄인다 (runbook 8절)
const AUTH_INTERVAL_SECONDS = Number(__ENV.AUTH_INTERVAL || 1.1);
const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const options = {
	vus: 1,
	iterations: 1,
	setupTimeout: '40m',
	thresholds: { checks: ['rate==1'] },
};

function login(email) {
	sleep(AUTH_INTERVAL_SECONDS);
	return http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({ email, password: PASSWORD }),
		{ headers: JSON_HEADERS, responseCallback: http.expectedStatuses(200, 401) });
}

function join(i, email) {
	sleep(AUTH_INTERVAL_SECONDS);
	// 운영 필수 약관 3종 (AGE_OVER_14 포함, 2026-11-17 CRU-22 추가)
	const res = http.post(`${BASE_URL}/api/v1/auth/join`, JSON.stringify({
		email, password: PASSWORD, nickname: `load${String(i).padStart(5, '0')}`,
		agreedTerms: ['SERVICE', 'PRIVACY', 'AGE_OVER_14'], verificationToken: `loadtest-${i}`,
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
		const cookie = res.cookies.refreshToken;
		accounts.push(ok ? { accessToken: res.json('data.accessToken'), refreshToken: cookie ? cookie[0].value : null } : null);
	}
	return { issuedAt: Date.now(), accounts };
}

export default function () {
}

export function handleSummary(data) {
	const { issuedAt, accounts } = data.setup_data;
	return {
		'./.accounts.json': JSON.stringify(accounts),
		'./.accounts-issued-at.txt': String(issuedAt),
		stdout: `토큰 ${accounts.filter(Boolean).length}/${accounts.length}개를 ./.accounts.json 에 저장했습니다\n`,
	};
}
