// 회원·크루·문의·게시판 부하 테스트가 같이 쓰는 값과 함수
// 기본 대상은 운영 서버다. 운영 DB는 발표 전에 비우기로 해서 테스트 데이터가 쌓여도 된다 (2026-11-16 결정)
import http from 'k6/http';
import { SharedArray } from 'k6/data';

export const BASE_URL = __ENV.BASE_URL || 'https://crewrun.duckdns.org';
export const API = `${BASE_URL}/api/v1`;

// 단계: smoke(스크립트가 도는지 1초에 1번씩) → load(평소 목표 부하 5분) → stress(목표의 4배까지 10분 동안 올리기)
const PRESETS = {
	smoke: { minutes: 0.5, scale: 0 },
	load: { minutes: 5, scale: 1 },
	stress: { minutes: 10, scale: 4, ramp: true },
};
export const STAGE_NAME = __ENV.STAGE || 'smoke';
export const STAGE = PRESETS[STAGE_NAME];
if (!STAGE) {
	throw new Error(`STAGE는 ${Object.keys(PRESETS).join(' / ')} 중 하나여야 합니다`);
}

// accounts/login.js가 만든 계정 파일. [{ accessToken, refreshToken }], 0번이 loadtest1 회원이다
// open()은 이 파일(lib/common.js) 기준 경로로 읽는다
export const accounts = new SharedArray('accounts', () => JSON.parse(open('../accounts/.accounts.json')));
const issuedAt = Number(open('../accounts/.accounts-issued-at.txt'));

// Access Token은 30분짜리다. 이번 단계가 끝날 때까지 3분 여유가 없으면 시작하지 않는다
export function assertTokensFresh(setupMinutes = 1) {
	const ageMinutes = (Date.now() - issuedAt) / 60000;
	if (ageMinutes + STAGE.minutes + setupMinutes > 27) {
		throw new Error(`토큰을 만든 지 ${Math.floor(ageMinutes)}분이라 실행 중에 만료됩니다. `
			+ 'accounts/login.js를 다시 돌린 뒤 바로 실행하세요');
	}
}

// 시나리오 하나를 단계에 맞게 만든다. rate는 load 단계의 초당 요청 수, cap은 어떤 단계에서도 넘지 않을 상한
export function arrival(exec, rate, cap = Infinity) {
	const target = Math.max(1, Math.min(cap, Math.round(rate * STAGE.scale)));
	const vus = {
		preAllocatedVUs: Math.max(2, target * 2),
		maxVUs: Math.max(10, target * 10),
	};
	if (STAGE.ramp) {
		return {
			executor: 'ramping-arrival-rate', exec, startRate: 0, timeUnit: '1s',
			stages: [{ target, duration: `${STAGE.minutes}m` }], ...vus,
		};
	}
	return {
		executor: 'constant-arrival-rate', exec, rate: target, timeUnit: '1s',
		duration: `${STAGE.minutes * 60}s`, ...vus,
	};
}

// 통과 기준은 시나리오 A(backend/loadtest/scenario-a)와 같다
// 전체 실패율이 5%를 넘으면 30초 뒤부터 테스트를 멈춘다. 운영 서버를 계속 두드리지 않게 하는 안전장치다
export const thresholds = {
	'http_req_duration{kind:read}': ['p(95)<300'],
	'http_req_duration{kind:write}': ['p(95)<1000'],
	'http_req_failed{kind:read}': ['rate<0.01'],
	'http_req_failed{kind:write}': ['rate<0.01'],
	http_req_failed: [{ threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: '30s' }],
	checks: ['rate>0.99'],
	dropped_iterations: ['count==0'],
};

// kind는 통과 기준 묶음, name은 주소를 묶는 이름(주소에 id가 들어가도 한 줄로 모인다)
export function params(token, kind, name, expected = [200], extraHeaders = {}) {
	const headers = { 'Content-Type': 'application/json', ...extraHeaders };
	if (token) {
		headers.Authorization = `Bearer ${token}`;
	}
	return { headers, tags: { kind, name }, responseCallback: http.expectedStatuses(...expected) };
}

// 계정을 차례로 돌려 쓴다. 같은 계정이 다시 오기까지 accounts.length / 초당 횟수 초가 걸린다
export function accountAt(i) {
	return accounts[i % accounts.length];
}

export function pick(list) {
	return list[Math.floor(Math.random() * list.length)];
}
