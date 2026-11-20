// 크루 화면 흐름 E2E 부하 테스트: 크루 탭 → 초대 → 초대받기 → 크루 나가기 → 크루 관리(5번에 한 번)
// 가상 사용자 1명이 계정 짝을 맡는다. 짝수 번 계정이 크루장, 바로 다음 홀수 번 계정이 초대받는 사람이다
// 화면이 지금 보내는 요청을 그대로 보낸다. 초대를 만들 때마다 목록을 다시 조회하는 낭비 요청도 넣었다
// 초대받는 사람은 가입하고 바로 나가서(탈퇴는 재가입 제한이 없다) 크루 인원이 쌓이지 않고 반복할 수 있다
// 준비(setup): 크루장마다 크루장인 크루가 없으면 하나 만든다
// 이 스크립트는 Access Token만 쓰고 Refresh Token은 쓰지 않아서 login.js를 다시 돌리지 않고 이어서 돌릴 수 있다(토큰 30분 안에서)
// 실행(backend/에서): k6 run -e STAGE=smoke loadtest/e2e/crew.js (smoke → load → stress)
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import exec from 'k6/execution';
import {
	API, accounts, assertTokensFresh, e2eExecutor, isAuthLost, params, screenThresholds, thinkTime,
} from '../lib/common.js';

assertTokensFresh(2);

// 크루 관리(이름 바꾸기)는 이 횟수에 한 번만 한다
const MANAGE_EVERY = Number(__ENV.MANAGE_EVERY || 5);
const SCREENS = ['크루 탭', '크루원 초대', '초대 받기', '크루 나가기', '크루 관리'];
const PAIRS = Math.floor(accounts.length / 2);

export const options = {
	setupTimeout: '3m',
	scenarios: { crew: e2eExecutor(PAIRS) },
	thresholds: screenThresholds(SCREENS),
};

// 돌려주는 값: crews[p] = p번 짝(크루장 계정 2p, 초대받는 계정 2p+1)의 크루 id
export function setup() {
	const crews = [];
	for (let p = 0; p < PAIRS; p++) {
		const leader = accounts[2 * p];
		if (!leader) {
			crews.push(null);
			continue;
		}
		const token = leader.accessToken;
		const mine = http.get(`${API}/member/crews/me`, params(token, 'setup', 'setup GET /member/crews/me'));
		const led = (mine.status === 200 ? mine.json('data') : []).find((crew) => crew.myRole === 'LEADER');
		if (led) {
			crews.push(led.crewId);
			continue;
		}
		const created = http.post(`${API}/member/crews`, JSON.stringify({ name: `e2e${p + 1}` }),
			params(token, 'setup', 'setup POST /member/crews', [201]));
		check(created, { '크루 만듦': (r) => r.status === 201 });
		crews.push(created.status === 201 ? created.json('data.crewId') : null);
	}
	return { crews };
}

// VU 변수
let pair = null;
let authLost = false;

function init(data) {
	const p = exec.vu.idInTest - 1;
	const leader = accounts[2 * p];
	const invitee = accounts[2 * p + 1];
	pair = {
		iteration: 0,
		crewId: data.crews[p],
		leaderToken: leader && leader.accessToken,
		inviteeToken: invitee && invitee.accessToken,
	};
}

function send(method, path, token, name, { expected = [200], body = null } = {}) {
	const payload = body === null ? null : JSON.stringify(body);
	const kind = method === 'GET' ? 'read' : 'write';
	const res = http.request(method, `${API}${path}`, payload, params(token, kind, `${method} ${name}`, expected));
	if (isAuthLost(res)) {
		authLost = true;
	}
	return res;
}

function crewTab() {
	const base = `/member/crews/${pair.crewId}`;
	check(send('GET', '/member/crews/me', pair.leaderToken, '/member/crews/me'), { '내 크루 200': (r) => r.status === 200 });
	check(send('GET', base, pair.leaderToken, '/member/crews/{crewId}'), { '크루 홈 200': (r) => r.status === 200 });
	send('GET', `/order/parties/crews/${pair.crewId}/active`, pair.leaderToken, '/order/parties/crews/{crewId}/active');
}

// 초대 토큰은 만들기 응답의 data.inviteToken에 있다(CrewInviteCreatedDto)
let inviteToken = null;
function invite() {
	const base = `/member/crews/${pair.crewId}/invites`;
	send('GET', '/member/members/me', pair.leaderToken, '/member/members/me');
	send('GET', base, pair.leaderToken, '/member/crews/{crewId}/invites');
	const created = send('POST', base, pair.leaderToken, '/member/crews/{crewId}/invites', { expected: [201] });
	inviteToken = check(created, { '초대 링크 201': (r) => r.status === 201 }) ? created.json('data.inviteToken') : null;
	send('GET', base, pair.leaderToken, '/member/crews/{crewId}/invites');
}

function accept() {
	const path = `/member/crews/invites/${inviteToken}`;
	check(send('GET', path, pair.inviteeToken, '/member/crews/invites/{inviteToken}'), { '초대 조회 200': (r) => r.status === 200 });
	// 지난 반복에서 나가기가 실패해 이미 크루원이면 409다. 아래 나가기 화면이 정리하므로 흐름은 이어 간다
	const joined = send('POST', `${path}/join`, pair.inviteeToken, '/member/crews/invites/{inviteToken}/join',
		{ expected: [201, 409] });
	check(joined, { '가입 201': (r) => r.status === 201 });
	send('GET', '/member/crews/me', pair.inviteeToken, '/member/crews/me');
}

function leave() {
	const left = send('DELETE', `/member/crews/${pair.crewId}/members/me`, pair.inviteeToken,
		'/member/crews/{crewId}/members/me');
	check(left, { '크루 나가기 200': (r) => r.status === 200 });
	send('GET', '/member/crews/me', pair.inviteeToken, '/member/crews/me');
}

function manage() {
	const renamed = send('PATCH', `/member/crews/${pair.crewId}`, pair.leaderToken, '/member/crews/{crewId}',
		{ body: { name: `e2e${exec.vu.idInTest}${pair.iteration % 2 === 0 ? 'a' : 'b'}` } });
	check(renamed, { '크루 이름 수정 200': (r) => r.status === 200 });
	send('GET', `/member/crews/${pair.crewId}`, pair.leaderToken, '/member/crews/{crewId}');
}

export default function (data) {
	if (pair === null) {
		init(data);
	}
	if (!pair.crewId || !pair.leaderToken || !pair.inviteeToken) {
		sleep(5);
		return;
	}
	authLost = false;
	pair.iteration++;
	const flow = [crewTab, invite, accept, leave];
	if (MANAGE_EVERY > 0 && pair.iteration % MANAGE_EVERY === 0) {
		flow.push(manage);
	}
	for (let i = 0; i < flow.length; i++) {
		group(SCREENS[i], flow[i]);
		// 초대 링크가 없으면 다음 화면(초대 받기)을 할 수 없다
		if (authLost || (i === 1 && inviteToken === null)) {
			// 바로 다시 시작하면 같은 실패로 서버를 계속 두드리게 되어 잠시 쉰다
			sleep(5);
			return;
		}
		thinkTime();
	}
}
