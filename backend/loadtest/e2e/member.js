// 회원 화면 흐름 E2E 부하 테스트: 앱 열기·마이페이지 → 정보 수정 → 알림 설정 → 로그아웃과 로그인
// 가상 사용자 1명이 계정 하나로 화면 순서대로 움직이고, 화면이 지금 보내는 요청을 그대로 보낸다
// 낭비 요청(주문마다 상세 조회, 저장 뒤 다시 조회)도 일부러 넣었다. 고친 뒤 좋아진 만큼을 재기 위해서다
// 계정은 VU 번호로 고른다. Refresh Token은 회전하고 이미 쓴 토큰이 오면 로그인이 끊겨서 계정 하나는 VU 하나만 쓴다
// 쓰고 나면 파일의 Refresh Token은 다 쓴 것이라 다시 돌리기 전에 accounts/login.js를 다시 돌린다
// 실행(backend/에서): LOADTEST_PASSWORD='…' k6 run -e STAGE=smoke loadtest/e2e/member.js (smoke → load → stress)
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import exec from 'k6/execution';
import {
	API, accounts, assertTokensFresh, e2eExecutor, isAuthLost, params, screenThresholds, thinkTime,
} from '../lib/common.js';

assertTokensFresh();

const PASSWORD = __ENV.LOADTEST_PASSWORD;
if (!PASSWORD) {
	throw new Error('LOADTEST_PASSWORD 환경 변수가 없습니다');
}
// 로그아웃·로그인은 운영 nginx 제한(IP당 초당 1건, 몰림 20건)에 걸린다. 100명이면 10번에 한 번이어도 넘어서 사용자가 하나씩 멈춘다
// 그래서 기본은 끄고(0), CRU-83 스위치로 제한을 끈 실행에서만 -e LOGIN_EVERY=10처럼 켠다
const LOGIN_EVERY = Number(__ENV.LOGIN_EVERY || 0);
// 마이페이지는 주문마다 상세를 한 건씩 부른다. 주문이 아주 많은 계정이 시간을 다 쓰지 않게 상한을 둔다
const ORDER_DETAIL_MAX = Number(__ENV.ORDER_DETAIL_MAX || 10);

const SCREENS = ['앱 열기·마이페이지', '정보 수정', '알림 설정', '로그아웃과 로그인'];

export const options = {
	scenarios: { member: e2eExecutor(accounts.length) },
	thresholds: screenThresholds(SCREENS),
};

// VU 변수. 토큰은 여기에 들고 다닌다. k6는 반복이 끝날 때마다 쿠키 저장소를 비워서 저장소에 두면 다음 반복에 쿠키가 빠진다
let me = null;
// 이번 반복에서 401·403을 받았는지
let authLost = false;

function init() {
	const no = exec.vu.idInTest;
	const account = accounts[no - 1];
	me = {
		no,
		email: `loadtest${no}@crewrun.test`,
		iteration: 0,
		dead: !account || !account.refreshToken,
		accessToken: account && account.accessToken,
		refreshToken: account && account.refreshToken,
	};
}

function send(method, path, kind, name, { expected = [200], body = null, cookie = null, token = me.accessToken } = {}) {
	const extra = cookie ? { Cookie: `refreshToken=${cookie}` } : {};
	const payload = body === null ? null : JSON.stringify(body);
	const res = http.request(method, `${API}${path}`, payload, params(token, kind, `${method} ${name}`, expected, extra));
	if (isAuthLost(res)) {
		authLost = true;
	}
	return res;
}

function openApp() {
	const res = send('POST', '/auth/reissue', 'write', '/auth/reissue', { token: null, cookie: me.refreshToken });
	const rotated = res.cookies.refreshToken;
	if (!check(res, { '재발급 200': (r) => r.status === 200 && Boolean(r.json('data.accessToken')) && Boolean(rotated) })) {
		// 재발급이 401이면 로그인이 끊긴 계정이다. 계속 두드리지 않는다
		me.dead = res.status === 401;
		authLost = true;
		return;
	}
	me.accessToken = res.json('data.accessToken');
	me.refreshToken = rotated[0].value;

	send('GET', '/notification/notifications', 'read', '/notification/notifications');
	// 판매자 신청 내역이 없으면 404다
	send('GET', '/member/sellers/me', 'read', '/member/sellers/me', { expected: [200, 404] });
	send('GET', '/cash/wallets/balance', 'read', '/cash/wallets/balance');
	const orders = send('GET', '/order/orders?page=0&size=100', 'read', '/order/orders');
	if (!check(orders, { '주문 목록 200': (r) => r.status === 200 })) {
		return;
	}
	for (const order of orders.json('data.orders').slice(0, ORDER_DETAIL_MAX)) {
		send('GET', `/order/orders/${order.id}`, 'read', '/order/orders/{orderId}');
	}
}

// 닉네임은 회원마다 겹치면 안 되고 그대로면 저장하지 않는다. 그래서 계정 번호에 반복마다 바뀌는 글자를 붙인다
// 규칙은 한글 2~8자·영문 4~16자(MemberModifyRequestDto)
function editInfo() {
	const nickname = `nk${me.no}${me.iteration % 2 === 0 ? 'a' : 'b'}`;
	const saved = send('PUT', '/member/members/me', 'write', '/member/members/me', { body: { nickname } });
	check(saved, { '정보 수정 200': (r) => r.status === 200 });
	const res = send('GET', '/member/members/me', 'read', '/member/members/me');
	check(res, { '내 정보 200': (r) => r.status === 200 });
}

function notifySettings() {
	check(send('GET', '/member/members/me', 'read', '/member/members/me'), { '내 정보 200': (r) => r.status === 200 });
	check(send('GET', '/member/members/me/marketing-consent', 'read', '/member/members/me/marketing-consent'),
		{ '마케팅 동의 조회 200': (r) => r.status === 200 });
}

function relogin() {
	const out = send('POST', '/auth/logout', 'write', '/auth/logout', { cookie: me.refreshToken });
	// 로그아웃이 안 됐으면(nginx 제한 429 등) 토큰이 그대로 살아 있어 다음 반복을 이어 간다
	if (!check(out, { '로그아웃 200': (r) => r.status === 200 })) {
		return;
	}
	// 로그아웃하면 이 Refresh Token은 서버에서 지워진다. 로그인이 실패하면 이 계정은 더 쓸 수 없다
	me.dead = true;
	const res = send('POST', '/auth/login', 'write', '/auth/login',
		{ token: null, body: { email: me.email, password: PASSWORD } });
	const cookie = res.cookies.refreshToken;
	if (check(res, { '로그인 200': (r) => r.status === 200 && Boolean(r.json('data.accessToken')) && Boolean(cookie) })) {
		me.accessToken = res.json('data.accessToken');
		me.refreshToken = cookie[0].value;
		me.dead = false;
	}
}

export default function () {
	if (me === null) {
		init();
	}
	if (me.dead) {
		sleep(5);
		return;
	}
	authLost = false;
	me.iteration++;
	const flow = [openApp, editInfo, notifySettings];
	if (LOGIN_EVERY > 0 && me.iteration % LOGIN_EVERY === 0) {
		flow.push(relogin);
	}
	for (let i = 0; i < flow.length; i++) {
		group(SCREENS[i], flow[i]);
		if (authLost || me.dead) {
			// 바로 다시 시작하면 같은 실패로 서버를 계속 두드리게 되어 잠시 쉰다
			sleep(5);
			return;
		}
		thinkTime();
	}
}
