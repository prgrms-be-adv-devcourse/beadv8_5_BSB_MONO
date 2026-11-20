// 둘러보기(read) 부하 — 대회 목록→상세→크루→파티 조회. 초안: 응답 data.* 키는 실측 전 실제 응답으로 확인.
// 토큰은 ../../main_scenario/k6/login-prod.js로 먼저 만든다(.accounts.json, 30분 안에 실행).
// 실행(이 폴더에서): k6 run -e STAGE=load -e RACE=10 -e RACE_COURSE=20 browse-read.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';

const BASE_URL = __ENV.BASE_URL || 'https://crewrun.duckdns.org';
if (!/^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$|^https:\/\/crewrun\.duckdns\.org$/.test(BASE_URL)) {
	throw new Error(`허용되지 않은 주소라 멈춥니다: ${BASE_URL}`);
}

const accounts = new SharedArray('accounts', () => JSON.parse(open('../../main_scenario/k6/.accounts.json')));
const RACE_ID = Number(__ENV.RACE || 10);
const INCLUDE_SIMILAR = (__ENV.SIMILAR || 'on') !== 'off'; // 검색 서버 기여분 분리용

// iteration/s 목표. 한 iteration이 GET 여러 개라 총 RPS ≈ rate × (호출 수)
const STAGES = {
	smoke: { rate: 3, sec: 30, warmupSec: 0 },
	load: { rate: 20, sec: 120, warmupSec: 20 },
	stress: { rate: 60, sec: 120, warmupSec: 20 },
};
const STAGE = STAGES[__ENV.STAGE];
if (!STAGE) {
	throw new Error(`STAGE는 ${Object.keys(STAGES).join(' / ')} 중 하나여야 합니다`);
}

export const options = {
	scenarios: {
		browse: {
			executor: 'constant-arrival-rate', exec: 'browse',
			rate: STAGE.rate, timeUnit: '1s', duration: `${STAGE.sec}s`,
			preAllocatedVUs: STAGE.rate * 4, maxVUs: STAGE.rate * 12, // maxVUs 꼭 줄 것(guide G)
		},
	},
	thresholds: {
		'http_req_duration{kind:read,phase:main}': ['p(95)<300'],
		'http_req_failed{kind:read,phase:main}': ['rate<0.01'],
		http_req_failed: [{ threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: '30s' }],
		'http_req_duration{kind:read}': [{ threshold: 'p(99)<5000', abortOnFail: true, delayAbortEval: '2m' }],
		checks: ['rate>0.99'],
		dropped_iterations: ['count==0'],
	},
};

const OK = http.expectedStatuses(200);

function params(token, name) {
	const phase = exec.instance.currentTestRunDuration < STAGE.warmupSec * 1000 ? 'warmup' : 'main';
	return {
		headers: token ? { Authorization: `Bearer ${token}` } : {},
		tags: { kind: 'read', name, phase },
		responseCallback: OK,
	};
}

function tokenOf(i) {
	const a = accounts[i % accounts.length];
	return a && a.accessToken;
}

export function browse() {
	const token = tokenOf(exec.scenario.iterationInTest);

	// 1) 대회 목록
	const list = http.get(`${BASE_URL}/api/v1/product/races?sort=DEADLINE&page=0&size=20`,
		params(token, 'GET /api/v1/product/races'));
	check(list, { '대회 목록 200': (r) => r.status === 200 });

	// 2) 대회 상세 (목록에서 raceId를 뽑는 게 이상적. 초안은 고정 RACE_ID 사용)
	const raceId = RACE_ID;
	check(http.get(`${BASE_URL}/api/v1/product/races/${raceId}`,
		params(token, 'GET /api/v1/product/races/{raceId}')), { '대회 상세 200': (r) => r.status === 200 });

	// 3) 비슷한 대회 (검색 서버 벡터 검색 — SIMILAR=off로 빼고 비교)
	if (INCLUDE_SIMILAR) {
		http.get(`${BASE_URL}/api/v1/product/races/${raceId}/similar`,
			params(token, 'GET /api/v1/product/races/{raceId}/similar'));
	}

	// 4) 이 대회에서 내 파티 상태 (파티 없어도 200)
	http.get(`${BASE_URL}/api/v1/order/parties/me/race-status?raceId=${raceId}`,
		params(token, 'GET /api/v1/order/parties/me/race-status'));

	sleep(1);

	// 5) 내 크루 목록 → crewId
	const crews = http.get(`${BASE_URL}/api/v1/member/crews/me`, params(token, 'GET /api/v1/member/crews/me'));
	let crewId = null;
	try { crewId = crews.json('data.0.id'); } catch (e) { crewId = null; } // 키는 실측 전 확인

	if (crewId != null) {
		// 6) 크루 홈
		http.get(`${BASE_URL}/api/v1/member/crews/${crewId}`, params(token, 'GET /api/v1/member/crews/{crewId}'));
		// 7) 크루의 진행 중 파티 → partyId
		const active = http.get(`${BASE_URL}/api/v1/order/parties/crews/${crewId}/active`,
			params(token, 'GET /api/v1/order/parties/crews/{crewId}/active'));
		let partyId = null;
		try { partyId = active.json('data.0.id'); } catch (e) { partyId = null; }
		// 8) 파티 상세
		if (partyId != null) {
			http.get(`${BASE_URL}/api/v1/order/parties/${partyId}`, params(token, 'GET /api/v1/order/parties/{partyId}'));
		}
	}
}
