// 회원 서비스 부하 테스트: 내 정보 조회, 마케팅 수신 동의 조회, 토큰 재발급
// 로그인·가입은 Nginx가 IP당 초당 1건으로 막아서 한 대에서는 부하를 줄 수 없다. 그래서 여기서는 빼고 accounts/login.js로만 쓴다
// 실행(backend/에서): k6 run -e STAGE=smoke loadtest/member/member.js (smoke → load → stress)
import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { API, STAGE, accounts, accountAt, arrival, assertTokensFresh, params, thresholds } from '../lib/common.js';

assertTokensFresh();

// 재발급용 계정: 뒤쪽 RESERVE명. Refresh Token은 쓰면 바뀌고, 이미 쓴 토큰이 다시 오면 서버가 탈취로 보고 로그인을 끊는다
// 그래서 계정 하나는 VU 하나만 쓴다. 이 스크립트를 한 번 돌리면 파일의 Refresh Token은 다 쓴 것이라, 다시 돌리기 전에 login.js를 다시 돌린다
const RESERVE = 50;
const REISSUE_VUS = Math.max(1, Math.min(RESERVE, Math.round(25 * STAGE.scale)));
const reissueFirst = accounts.length - RESERVE;

export const options = {
	scenarios: {
		me: arrival('me', 30),
		consent: arrival('consent', 10),
		// VU마다 1초에 한 번 재발급한다. load는 초당 약 25번
		reissue: { executor: 'constant-vus', exec: 'reissue', vus: REISSUE_VUS, duration: `${STAGE.minutes * 60}s` },
	},
	thresholds,
};

export function me() {
	const account = accountAt(exec.scenario.iterationInTest);
	const res = http.get(`${API}/member/members/me`, params(account.accessToken, 'read', 'GET /member/members/me'));
	check(res, { '내 정보 200': (r) => r.status === 200 });
}

export function consent() {
	const account = accountAt(exec.scenario.iterationInTest);
	const res = http.get(`${API}/member/members/me/marketing-consent`,
		params(account.accessToken, 'read', 'GET /member/members/me/marketing-consent'));
	check(res, { '마케팅 동의 조회 200': (r) => r.status === 200 });
}

// VU마다 처음 받은 순번으로 계정을 고른다. constant-vus는 VU들이 동시에 시작해서 처음 순번 0..VU수-1이 VU마다 하나씩 간다
// 토큰은 VU 변수로 들고 다닌다. k6는 반복이 끝날 때마다 쿠키 저장소를 비워서 저장소에 두면 두 번째 반복부터 쿠키가 빠진다
let refreshToken = null;
export function reissue() {
	if (refreshToken === null) {
		const no = reissueFirst + exec.scenario.iterationInTest;
		refreshToken = accounts[no] && accounts[no].refreshToken;
		if (!refreshToken) {
			throw new Error(`재발급용 계정 ${no}에 Refresh Token이 없습니다`);
		}
	}
	const res = http.post(`${API}/auth/reissue`, null,
		params(null, 'write', 'POST /auth/reissue', [200], { Cookie: `refreshToken=${refreshToken}` }));
	const rotated = res.cookies.refreshToken;
	if (check(res, { '재발급 200': (r) => r.status === 200 && Boolean(r.json('data.accessToken')) && Boolean(rotated) })) {
		refreshToken = rotated[0].value;
	}
	sleep(1);
}
