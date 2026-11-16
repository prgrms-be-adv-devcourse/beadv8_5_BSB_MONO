// 시나리오 A 실행 직전에 테스트 회원을 로그인시켜 토큰을 파일로 저장한다 (Access Token은 30분짜리)
// 본 스크립트(scenario-a.js)는 이 파일을 모든 VU가 같이 읽는다(SharedArray). setup()이 돌려준 값은 VU마다 복사돼 메모리를 많이 쓴다
// 실행(backend/에서): LOADTEST_PASSWORD='…' k6 run loadtest/scenario-a/login.js
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8000';
if (!/^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(BASE_URL)) {
	throw new Error(`로컬 주소가 아니라 멈춥니다: ${BASE_URL}`);
}
const PASSWORD = __ENV.LOADTEST_PASSWORD;
if (!PASSWORD) {
	throw new Error('LOADTEST_PASSWORD 환경 변수가 없습니다');
}
const COUNT = Number(__ENV.COUNT || 6000);
const BATCH = 50;
const OUT_DIR = __ENV.OUT_DIR || 'loadtest/scenario-a';

export const options = {
	vus: 1,
	iterations: 1,
	setupTimeout: '15m',
	// 한 번에 50명씩 동시에 로그인한다 (기본값은 주소 하나에 6개씩)
	batch: BATCH,
	batchPerHost: BATCH,
	thresholds: { checks: ['rate==1'] },
};

export function setup() {
	const tokens = [];
	for (let start = 1; start <= COUNT; start += BATCH) {
		const requests = [];
		for (let i = start; i < Math.min(start + BATCH, COUNT + 1); i++) {
			requests.push(['POST', `${BASE_URL}/api/v1/auth/login`,
				JSON.stringify({ email: `loadtest${i}@crewrun.test`, password: PASSWORD }),
				{ headers: { 'Content-Type': 'application/json' } }]);
		}
		for (const res of http.batch(requests)) {
			check(res, { '로그인됨': (r) => r.status === 200 });
			// tokens[0]이 loadtest1 회원이다
			tokens.push(res.status === 200 ? res.json('data.accessToken') : null);
		}
	}
	return { issuedAt: Date.now(), tokens };
}

export default function () {
}

// 끝날 때 토큰을 파일로 쓴다. handleSummary만 파일을 쓸 수 있다
export function handleSummary(data) {
	const { issuedAt, tokens } = data.setup_data;
	return {
		[`${OUT_DIR}/.tokens.json`]: JSON.stringify(tokens),
		[`${OUT_DIR}/.tokens-issued-at.txt`]: String(issuedAt),
		stdout: `토큰 ${tokens.filter(Boolean).length}/${tokens.length}개를 ${OUT_DIR}/.tokens.json에 저장했습니다\n`,
	};
}
