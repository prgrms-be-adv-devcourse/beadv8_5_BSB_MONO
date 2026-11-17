// 크루 부하 테스트: 내 크루 목록, 크루 홈 조회, 초대 링크 만들기
// 준비(setup): 계정마다 크루가 없으면 하나 만든다. 그래서 모든 계정이 자기 크루의 크루장이다
// 실행(backend/에서): k6 run -e STAGE=smoke loadtest/crew/crew.js (smoke → load → stress)
import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { API, accounts, arrival, assertTokensFresh, params, thresholds } from '../lib/common.js';

assertTokensFresh(2);

export const options = {
	setupTimeout: '3m',
	scenarios: {
		myCrews: arrival('myCrews', 20),
		crewHome: arrival('crewHome', 20),
		invite: arrival('invite', 2),
	},
	thresholds,
};

// 돌려주는 값: crews[i] = i번 계정의 크루 id
export function setup() {
	const crews = [];
	for (let i = 0; i < accounts.length; i++) {
		const token = accounts[i].accessToken;
		const mine = http.get(`${API}/member/crews/me`, params(token, 'setup', 'setup GET /member/crews/me'));
		const existing = mine.status === 200 ? mine.json('data') : [];
		if (existing.length > 0) {
			crews.push(existing[0].crewId);
			continue;
		}
		const created = http.post(`${API}/member/crews`, JSON.stringify({ name: `부하${i + 1}` }),
			params(token, 'setup', 'setup POST /member/crews', [201]));
		check(created, { '크루 만듦': (r) => r.status === 201 });
		crews.push(created.status === 201 ? created.json('data.crewId') : null);
	}
	return { crews };
}

function pickAccount(data) {
	const i = exec.scenario.iterationInTest % accounts.length;
	return { token: accounts[i].accessToken, crewId: data.crews[i] };
}

export function myCrews(data) {
	const { token } = pickAccount(data);
	const res = http.get(`${API}/member/crews/me`, params(token, 'read', 'GET /member/crews/me'));
	check(res, { '내 크루 200': (r) => r.status === 200 });
}

export function crewHome(data) {
	const { token, crewId } = pickAccount(data);
	const res = http.get(`${API}/member/crews/${crewId}`, params(token, 'read', 'GET /member/crews/{crewId}'));
	check(res, { '크루 홈 200': (r) => r.status === 200 });
}

export function invite(data) {
	const { token, crewId } = pickAccount(data);
	const res = http.post(`${API}/member/crews/${crewId}/invites`, null,
		params(token, 'write', 'POST /member/crews/{crewId}/invites', [201]));
	check(res, { '초대 링크 201': (r) => r.status === 201 });
}
