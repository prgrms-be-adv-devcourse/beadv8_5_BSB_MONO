// 문의 부하 테스트: 내 문의 목록, 문의 상세, 문의 남기기
// 문의 종류는 ETC(기타)만 쓴다. 담당이 관리자라 대회·주문 없이 남길 수 있고, 생성 때 주문·상품 서비스를 부르지 않는다
// 준비(setup): 계정마다 문의가 없으면 하나 남긴다
// 실행(backend/에서): k6 run -e STAGE=smoke loadtest/inquiry/inquiry.js (smoke → load → stress)
import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { API, accounts, arrival, assertTokensFresh, params, thresholds } from '../lib/common.js';

assertTokensFresh(2);

export const options = {
	setupTimeout: '3m',
	scenarios: {
		list: arrival('list', 20),
		detail: arrival('detail', 20),
		create: arrival('create', 2),
	},
	thresholds,
};

function body(no) {
	return JSON.stringify({ type: 'ETC', title: `부하 테스트 문의 ${no}`, body: '부하 테스트로 남긴 문의입니다.' });
}

// 돌려주는 값: inquiries[i] = i번 계정의 문의 id
export function setup() {
	const inquiries = [];
	for (let i = 0; i < accounts.length; i++) {
		const token = accounts[i].accessToken;
		const mine = http.get(`${API}/inquiry/inquiries/me?page=0&size=1`, params(token, 'setup', 'GET /inquiry/inquiries/me'));
		const existing = mine.status === 200 ? mine.json('data.inquiries') : [];
		if (existing.length > 0) {
			inquiries.push(existing[0].id);
			continue;
		}
		const created = http.post(`${API}/inquiry/inquiries`, body(0), params(token, 'setup', 'POST /inquiry/inquiries', [201]));
		check(created, { '문의 남김': (r) => r.status === 201 });
		inquiries.push(created.status === 201 ? created.json('data.id') : null);
	}
	return { inquiries };
}

function index() {
	return exec.scenario.iterationInTest % accounts.length;
}

export function list() {
	const res = http.get(`${API}/inquiry/inquiries/me?page=0&size=20`,
		params(accounts[index()].accessToken, 'read', 'GET /inquiry/inquiries/me'));
	check(res, { '내 문의 목록 200': (r) => r.status === 200 });
}

export function detail(data) {
	const i = index();
	const res = http.get(`${API}/inquiry/inquiries/${data.inquiries[i]}`,
		params(accounts[i].accessToken, 'read', 'GET /inquiry/inquiries/{inquiryId}'));
	check(res, { '문의 상세 200': (r) => r.status === 200 });
}

export function create() {
	const res = http.post(`${API}/inquiry/inquiries`, body(exec.scenario.iterationInTest + 1),
		params(accounts[index()].accessToken, 'write', 'POST /inquiry/inquiries', [201]));
	check(res, { '문의 남김 201': (r) => r.status === 201 });
}
