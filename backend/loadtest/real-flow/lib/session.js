// 가상 사용자 한 명의 로그인 상태와 요청 (화면의 customFetch와 같게 동작한다)
// - Access Token은 메모리(이 객체)에만 두고 Authorization 헤더로 붙인다
// - Refresh Token은 k6의 가상 사용자별 쿠키 저장소에 둔다 (서버가 Path=/api/v1/auth 쿠키로 준다)
// - 401이면 재발급을 한 번 보내고 원래 요청을 한 번 다시 보낸다
import http from 'k6/http';
import { API, API_URL, BASE_URL, SEND_PAGES } from '../config.js';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

// 요청 이름(name)은 주소의 id를 {..}로 바꾼 것. 결과가 주소마다 따로 흩어지지 않게 한다
// kind: page(화면) / read(조회) / queue(대기열) / write(신청·주문·결제) / auth(로그인·재발급)
function tags(kind, name, extra) {
	return { kind, name, ...extra };
}

export class Session {
	constructor(scenario) {
		this.token = null;
		this.scenario = scenario;
	}

	// 준비 단계(setup)에서 받은 Refresh Token을 쿠키 저장소에 넣는다 ("전에 로그인해 둔 사람")
	useRefreshToken(refreshToken) {
		http.cookieJar().set(API_URL, 'refreshToken', refreshToken, { path: '/api/v1/auth' });
	}

	forgetCookies() {
		http.cookieJar().clear(API_URL);
		this.token = null;
	}

	reissue() {
		const res = http.post(`${API}/auth/reissue`, null, {
			headers: JSON_HEADERS,
			tags: tags('auth', 'POST /auth/reissue', { scenario: this.scenario }),
			responseCallback: http.expectedStatuses(200, 401),
		});
		this.token = res.status === 200 ? res.json('data.accessToken') : null;
		return res;
	}

	login(email, password) {
		const res = http.post(`${API}/auth/login`, JSON.stringify({ email, password }), {
			headers: JSON_HEADERS,
			tags: tags('auth', 'POST /auth/login', { scenario: this.scenario }),
		});
		this.token = res.status === 200 ? res.json('data.accessToken') : null;
		return res;
	}

	// API 요청. expected에 없는 응답은 실패로 센다
	api(method, path, { body = null, name, kind = 'read', expected = [200], headers = {} } = {}) {
		const send = () => http.request(method, `${API}${path}`, body === null ? null : JSON.stringify(body), {
			headers: { ...JSON_HEADERS, ...(this.token ? { Authorization: `Bearer ${this.token}` } : {}), ...headers },
			tags: tags(kind, name || `${method} ${path}`, { scenario: this.scenario }),
			responseCallback: http.expectedStatuses(...expected, 401),
		});
		let res = send();
		if (res.status === 401 && this.reissue().status === 200) {
			res = send();
		}
		return res;
	}

	// 화면 이동. 앱 안에서 링크를 누르면 Next가 ?_rsc=와 RSC 헤더로 화면 데이터를 받는다
	// Next 서버가 이 요청을 그리면서 백엔드를 부르는 것(대회 목록·상세 등)도 함께 일어난다
	// first=true는 주소를 새로 연 것(문서 전체)
	page(path, name, { first = false } = {}) {
		if (!SEND_PAGES) {
			return null;
		}
		const url = first ? `${BASE_URL}${path}` : `${BASE_URL}${path}${path.includes('?') ? '&' : '?'}_rsc=k6`;
		return http.get(url, {
			headers: first ? {} : { RSC: '1' },
			tags: tags('page', name, { scenario: this.scenario }),
		});
	}
}

export function data(res, path) {
	try {
		return res.json(path);
	} catch (e) {
		return null;
	}
}

export function code(res) {
	return data(res, 'code');
}
