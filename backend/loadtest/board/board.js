// 게시판 부하 테스트: 글 목록(최신·인기·분류·검색), 글 상세, 댓글 목록, 댓글 쓰기, 좋아요, 글 쓰기
// 읽기는 로그인 없이 부른다(공개 주소). 쓰기는 회원마다 글 1분에 1개, 댓글 10초에 1개로 막혀 있어서(BoardCooldown)
// 계정을 차례로 돌려 같은 계정이 다시 오기까지 간격이 그보다 길도록 초당 횟수에 상한(cap)을 둔다
// 실행(backend/에서): k6 run -e STAGE=smoke loadtest/board/board.js (smoke → load → stress)
import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { API, accounts, arrival, assertTokensFresh, params, pick, thresholds } from '../lib/common.js';

assertTokensFresh(2);

const SEED_POSTS = 30;
const CATEGORIES = ['FREE', 'RACE_REVIEW', 'QUESTION', 'RUN_TOGETHER'];
// 계정 수 ÷ 간격(초) = 같은 계정이 간격 안에 다시 오지 않는 최대 초당 횟수. 여유를 5초 둔다
const POST_CAP = Math.floor(accounts.length / 65);
const COMMENT_CAP = Math.floor(accounts.length / 15);

export const options = {
	setupTimeout: '3m',
	scenarios: {
		latest: arrival('latest', 40),
		popular: arrival('popular', 10),
		category: arrival('category', 10),
		search: arrival('search', 5),
		detail: arrival('detail', 30),
		comments: arrival('comments', 30),
		comment: arrival('comment', 5, COMMENT_CAP),
		like: arrival('like', 5),
		post: arrival('post', 1, POST_CAP),
	},
	thresholds,
};

function postBody(no) {
	return JSON.stringify({ category: pick(CATEGORIES), title: `부하 테스트 글 ${no}`, content: '부하 테스트로 쓴 글입니다.' });
}

// 글이 SEED_POSTS개보다 적으면 뒤쪽 계정들로 채운다. 글 쓰기 시나리오는 앞쪽 계정부터 써서 1분 간격에 걸리지 않는다
// 돌려주는 값: posts = 상세·댓글·좋아요에 쓸 글 id
export function setup() {
	const list = http.get(`${API}/board/posts?size=50`, params(null, 'setup', 'GET /board/posts'));
	const posts = list.status === 200 ? list.json('data').map((p) => p.postId) : [];
	for (let i = accounts.length - 1; posts.length < SEED_POSTS; i--) {
		const res = http.post(`${API}/board/posts`, postBody(`준비 ${posts.length + 1}`),
			params(accounts[i].accessToken, 'setup', 'POST /board/posts', [201]));
		if (!check(res, { '준비 글 201': (r) => r.status === 201 })) {
			throw new Error(`준비 글을 쓰지 못했습니다: ${res.status} ${res.body}`);
		}
		posts.push(res.json('data.postId'));
	}
	return { posts };
}

function account() {
	return accounts[exec.scenario.iterationInTest % accounts.length];
}

function listOf(query, name) {
	const res = http.get(`${API}/board/posts?${query}`, params(null, 'read', name));
	check(res, { '글 목록 200': (r) => r.status === 200 });
}

export function latest() {
	listOf('size=20', 'GET /board/posts');
}

export function popular() {
	listOf('sort=POPULAR&size=20', 'GET /board/posts?sort=POPULAR');
}

export function category() {
	listOf(`category=${pick(CATEGORIES)}&size=20`, 'GET /board/posts?category');
}

export function search() {
	listOf(`q=${encodeURIComponent('부하')}&size=20`, 'GET /board/posts?q');
}

export function detail(data) {
	const res = http.get(`${API}/board/posts/${pick(data.posts)}`, params(null, 'read', 'GET /board/posts/{postId}'));
	check(res, { '글 상세 200': (r) => r.status === 200 });
}

export function comments(data) {
	const res = http.get(`${API}/board/posts/${pick(data.posts)}/comments`,
		params(null, 'read', 'GET /board/posts/{postId}/comments'));
	check(res, { '댓글 목록 200': (r) => r.status === 200 });
}

export function comment(data) {
	const res = http.post(`${API}/board/posts/${pick(data.posts)}/comments`,
		JSON.stringify({ content: '부하 테스트 댓글입니다.' }),
		params(account().accessToken, 'write', 'POST /board/posts/{postId}/comments', [201]));
	check(res, { '댓글 201': (r) => r.status === 201 });
}

// 좋아요를 누르고 바로 취소한다. 같은 글을 여러 번 눌러도 좋아요 수가 계속 늘지 않는다
export function like(data) {
	const token = account().accessToken;
	const postId = pick(data.posts);
	const on = http.put(`${API}/board/posts/${postId}/like`, null, params(token, 'write', 'PUT /board/posts/{postId}/like'));
	const off = http.del(`${API}/board/posts/${postId}/like`, null,
		params(token, 'write', 'DELETE /board/posts/{postId}/like'));
	check(on, { '좋아요 200': (r) => r.status === 200 });
	check(off, { '좋아요 취소 200': (r) => r.status === 200 });
}

export function post() {
	const res = http.post(`${API}/board/posts`, postBody(exec.scenario.iterationInTest + 1),
		params(account().accessToken, 'write', 'POST /board/posts', [201]));
	check(res, { '글 201': (r) => r.status === 201 });
}
