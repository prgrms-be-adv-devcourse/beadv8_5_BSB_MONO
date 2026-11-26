// real-flow 준비 데이터를 만든다 (처음 한 번, 또는 구간을 늘릴 때)
// 1. 테스트 계정 realflow1~COUNT 가입 (JOIN=1. 먼저 identities.sh로 본인인증 결과를 넣는다)
// 2. 예치금 충전 (CHARGE=1. 로컬만 된다: 결제사가 가짜. 운영은 sql/charge.sql)
// 3. 크루 CREWS개 만들기: 크루 i = 계정 5i+1(크루장) ~ 5i+5. 결과를 ../.crews.json(CREWS_FILE)에 쓴다
//    크루 가입일은 파티 규칙상 크루 예매 시작 7일 전이어야 해서 만든 뒤 sql/backdate-crews.sql을 돌린다
// 필요한 계정·크루 수는 rush.js와 같은 STAGES로 계산한다 (기본 100,300,600,1000 → 계정 2,500, 크루 320)
// 실행 방법은 README.md
import http from 'k6/http';
import { sleep } from 'k6';
import { API, PARTY_SIZE, PASSWORD, accountLayout, accountNoOf, emailOf } from '../config.js';

if (!PASSWORD) {
	throw new Error('LOADTEST_PASSWORD 환경 변수가 없습니다');
}
const layout = accountLayout();
const COUNT = Number(__ENV.COUNT || layout.total);
const CREWS = Number(__ENV.CREWS === undefined ? layout.crewCount : __ENV.CREWS);
const JOIN = __ENV.JOIN === '1';
const CHARGE = __ENV.CHARGE === '1';
const CHARGE_AMOUNT = Number(__ENV.CHARGE_AMOUNT || 300000);
const RUN = Date.now().toString(36);

export const options = { vus: 1, iterations: 1, setupTimeout: '60m', teardownTimeout: '1m' };

const JSON_HEADERS = { 'Content-Type': 'application/json' };
const auth = (token) => ({ headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` } });

function batch(requests) {
	const out = [];
	// 운영 회원 서비스(메모리 640Mi)는 가입 50건을 한꺼번에 받다 메모리 초과로 죽었다(2026-11-27). 운영 가입은 BATCH=10으로 돌린다
	const size = Number(__ENV.BATCH || 50);
	for (let i = 0; i < requests.length; i += size) {
		out.push(...http.batch(requests.slice(i, i + size)));
	}
	return out;
}

const loginReq = (no) => ({ method: 'POST', url: `${API}/auth/login`, body: JSON.stringify({ email: emailOf(no), password: PASSWORD }), params: { headers: JSON_HEADERS } });

export function setup() {
	const nos = Array.from({ length: COUNT }, (_, i) => i + 1);
	let logins = batch(nos.map(loginReq));

	if (JOIN) {
		const missing = nos.filter((no, i) => logins[i].status !== 200);
		const joined = batch(missing.map((no) => ({
			method: 'POST', url: `${API}/auth/join`,
			body: JSON.stringify({
				email: emailOf(no), password: PASSWORD, nickname: `rflow${accountNoOf(no)}`,
				agreedTerms: ['SERVICE', 'PRIVACY', 'AGE_OVER_14'], verificationToken: `realflow-${accountNoOf(no)}`,
			}),
			params: { headers: JSON_HEADERS },
		})));
		joined.forEach((r, i) => {
			if (r.status !== 201) {
				throw new Error(`${emailOf(missing[i])} 가입 실패 ${r.status} ${r.body}`);
			}
		});
		console.log(`가입 ${missing.length}명`);
		logins = batch(nos.map(loginReq));
	}
	const tokens = logins.map((r, i) => {
		if (r.status !== 200) {
			throw new Error(`${emailOf(nos[i])} 로그인 실패 ${r.status}. 가입하려면 JOIN=1`);
		}
		return r.json('data.accessToken');
	});

	if (CHARGE) {
		// 가입 메시지로 지갑이 생기기 전이면 404라 몇 번 다시 시도한다
		let pending = nos.map((no, i) => i);
		for (let attempt = 0; attempt < 10 && pending.length; attempt++) {
			const res = batch(pending.map((i) => ({
				method: 'POST', url: `${API}/cash/charges/confirm`,
				body: JSON.stringify({ paymentKey: `rf-pk-${RUN}-${nos[i]}`, orderId: `rf-${RUN}-${nos[i]}`, amount: CHARGE_AMOUNT }),
				params: auth(tokens[i]),
			})));
			const failed = res.map((r, k) => (r.status === 200 ? null : { i: pending[k], r })).filter(Boolean);
			const fatal = failed.find((f) => f.r.status !== 404);
			if (fatal) {
				throw new Error(`${emailOf(nos[fatal.i])} 충전 실패 ${fatal.r.status} ${fatal.r.body}`);
			}
			pending = failed.map((f) => f.i);
			if (pending.length) {
				sleep(2);
			}
		}
		console.log(`충전 ${COUNT - pending.length}명 × ${CHARGE_AMOUNT}원${pending.length ? `, 지갑 없음 ${pending.length}명` : ''}`);
	}

	const crews = [];
	for (let c = 0; c < CREWS; c++) {
		const accounts = Array.from({ length: PARTY_SIZE }, (_, k) => c * PARTY_SIZE + k + 1);
		const leader = tokens[accounts[0] - 1];
		const crew = http.post(`${API}/member/crews`, JSON.stringify({ name: `실흐름-${RUN}-${c}` }), auth(leader));
		if (crew.status !== 201) {
			throw new Error(`크루 ${c} 만들기 실패 ${crew.status} ${crew.body}`);
		}
		const crewId = crew.json('data.crewId');
		const invite = http.post(`${API}/member/crews/${crewId}/invites`, null, auth(leader));
		const inviteToken = invite.json('data.inviteToken');
		const joined = http.batch(accounts.slice(1).map((no) => ({
			method: 'POST', url: `${API}/member/crews/invites/${inviteToken}/join`, body: null, params: auth(tokens[no - 1]),
		})));
		joined.forEach((r) => {
			if (r.status !== 201) {
				throw new Error(`크루 ${c} 들어가기 실패 ${r.status} ${r.body}`);
			}
		});
		crews.push({ crewId, accounts });
	}
	console.log(`크루 ${crews.length}개 (이름 실흐름-${RUN}-*). 이제 sql/backdate-crews.sql을 돌리세요`);
	return { crews };
}

export default function () {}

export function handleSummary(summary) {
	const crews = summary.setup_data ? summary.setup_data.crews : [];
	if (!crews.length) {
		return { stdout: '크루를 만들지 않았습니다 (CREWS=0)\n' };
	}
	return { [__ENV.CREWS_FILE || '.crews.json']: JSON.stringify(crews), stdout: `${__ENV.CREWS_FILE || '.crews.json'}에 크루 ${crews.length}개를 썼습니다\n` };
}
