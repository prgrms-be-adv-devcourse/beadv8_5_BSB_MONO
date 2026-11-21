// 회원·크루·문의·게시판 부하 테스트가 같이 쓰는 값과 함수
// 기본 대상은 운영 서버다. 운영 DB는 발표 전에 비우기로 해서 테스트 데이터가 쌓여도 된다 (2026-11-16 결정)
import http from 'k6/http';
import { sleep } from 'k6';
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

// 통과 기준은 시나리오 A(docs/loadtest/scenario-a)와 같다
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

// 화면 흐름(e2e/) 테스트용: 가상 사용자 1명이 계정 하나를 맡아 화면 순서대로 움직인다
// 단계별 사용자 수: smoke 1명 30초, load 100명 5분, stress 0에서 200명까지 10분
const E2E_USERS = { smoke: 1, load: 100, stress: 200 };

// 이번 단계의 사용자 수. cap은 맡을 수 있는 최대 사용자 수(계정 수에서 나온다)
export function e2eUsers(cap = Infinity) {
	return Math.min(cap, E2E_USERS[STAGE_NAME]);
}

export function e2eExecutor(cap = Infinity) {
	const target = e2eUsers(cap);
	if (STAGE.ramp) {
		return { executor: 'ramping-vus', startVUs: 0, stages: [{ target, duration: `${STAGE.minutes}m` }] };
	}
	return { executor: 'constant-vus', vus: target, duration: `${STAGE.minutes * 60}s` };
}

// 계정 문제를 알린다. init 코드는 가상 사용자마다 돌아서 경고가 사용자 수만큼 찍히므로 setup()에서 한 번만 부른다
export function warnAccounts(cap = Infinity) {
	// login.js는 로그인에 실패한 계정 자리에 null을 넣는다. 그 번호를 맡은 사용자는 쉬기만 해서 부하가 줄어든다
	let missing = 0;
	for (let i = 0; i < accounts.length; i++) {
		if (!accounts[i]) {
			missing++;
		}
	}
	if (missing > 0) {
		console.warn(`로그인에 실패한 계정이 ${missing}개라 그 번호의 사용자는 요청을 보내지 않습니다. login.js를 다시 돌리세요`);
	}
	if (e2eUsers(cap) < E2E_USERS[STAGE_NAME]) {
		console.warn(`계정이 모자라 사용자를 ${E2E_USERS[STAGE_NAME]}명에서 ${e2eUsers(cap)}명으로 줄입니다`);
	}
}

// 화면(group)마다 p95 1초. group 태그 값은 앞에 ::가 붙는다
export function screenThresholds(names) {
	const result = { ...thresholds };
	for (const name of names) {
		result[`group_duration{group:::${name}}`] = ['p(95)<1000'];
	}
	return result;
}

// 화면 사이에 사람이 읽는 시간 3~5초
export function thinkTime() {
	sleep(3 + Math.random() * 2);
}

// 401·403이면 로그인이 끊긴 것이다. 그 반복은 더 두드리지 않고 끝낸다
export function isAuthLost(res) {
	return res.status === 401 || res.status === 403;
}
