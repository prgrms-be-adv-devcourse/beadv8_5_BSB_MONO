// 게이트웨이 CPU A/B 측정용 부하 (11/18, CRU-58). 대기열 순번 조회만 초당 RATE건, DURATION 동안 보낸다
// 실행(backend/에서): EMAIL='내 이메일' LOADTEST_PASSWORD='…' k6 run loadtest/gateway-cpu-ab.js (EMAIL을 빼면 loadtest1~200 계정)
// 응답이 404(줄에 없음)여도 게이트웨이는 토큰 검사·로그아웃 확인·중계를 똑같이 하므로 측정에는 상관없다
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'https://crewrun.duckdns.org';
const PASSWORD = __ENV.LOADTEST_PASSWORD;
if (!PASSWORD && __ENV.NO_TOKEN !== '1') {
	throw new Error('LOADTEST_PASSWORD 환경 변수가 없습니다');
}
const ACCOUNTS = Number(__ENV.ACCOUNTS || 200);
const RATE = Number(__ENV.RATE || 200);
const DURATION = __ENV.DURATION || '150s';
const RACE = __ENV.RACE || '1';
const COURSE = __ENV.COURSE || '1';
// TARGET을 주면 그 주소로 보낸다. NO_TOKEN=1이면 토큰 없이 보낸다 (공개 주소에서 토큰 있음·없음을 비교해 토큰 검사 비용을 잰다)
const TARGET = __ENV.TARGET || `/api/v1/queue/races/${RACE}/racecourses/${COURSE}`;
const NO_TOKEN = __ENV.NO_TOKEN === '1';

export const options = {
	setupTimeout: '5m',
	batch: 50,
	batchPerHost: 50,
	scenarios: {
		poll: {
			executor: 'constant-arrival-rate',
			rate: RATE, timeUnit: '1s', duration: DURATION,
			preAllocatedVUs: 200, maxVUs: 400,
		},
	},
	thresholds: { 'checks{step:login}': ['rate==1'] },
};

export function setup() {
	if (NO_TOKEN) {
		console.log(`토큰 없이 시작합니다: ${new Date().toISOString()}`);
		return { tokens: [] };
	}
	// EMAIL을 주면 그 계정 하나로만 로그인한다. 게이트웨이는 토큰 검사 결과를 저장하지 않아 토큰 1개여도 측정이 같다
	if (__ENV.EMAIL) {
		const res = http.post(`${BASE_URL}/api/v1/auth/login`,
			JSON.stringify({ email: __ENV.EMAIL, password: PASSWORD }),
			{ headers: { 'Content-Type': 'application/json' } });
		if (res.status !== 200) {
			throw new Error(`로그인 실패 (${res.status}). 이메일과 비밀번호를 확인하세요`);
		}
		console.log(`토큰 1개로 시작합니다: ${new Date().toISOString()}`);
		return { tokens: [res.json('data.accessToken')] };
	}
	const tokens = [];
	for (let start = 1; start <= ACCOUNTS; start += 50) {
		const requests = [];
		for (let i = start; i < Math.min(start + 50, ACCOUNTS + 1); i++) {
			requests.push(['POST', `${BASE_URL}/api/v1/auth/login`,
				JSON.stringify({ email: `loadtest${i}@crewrun.test`, password: PASSWORD }),
				{ headers: { 'Content-Type': 'application/json' }, tags: { step: 'login' } }]);
		}
		for (const res of http.batch(requests)) {
			check(res, { '로그인됨': (r) => r.status === 200 }, { step: 'login' });
			const token = res.status === 200 ? res.json('data.accessToken') : null;
			if (token) {
				tokens.push(token);
			}
		}
	}
	if (tokens.length === 0) {
		throw new Error('로그인한 계정이 없습니다. 비밀번호나 계정 이름을 확인하세요');
	}
	console.log(`토큰 ${tokens.length}개로 시작합니다: ${new Date().toISOString()}`);
	return { tokens };
}

export default function (data) {
	const headers = NO_TOKEN ? {}
		: { Authorization: `Bearer ${data.tokens[Math.floor(Math.random() * data.tokens.length)]}` };
	const res = http.get(`${BASE_URL}${TARGET}`, { headers, tags: { name: 'target' } });
	check(res, { '게이트웨이 통과(401 아님)': (r) => r.status !== 401 && r.status < 500 });
}

export function teardown() {
	console.log(`끝났습니다: ${new Date().toISOString()}`);
}
