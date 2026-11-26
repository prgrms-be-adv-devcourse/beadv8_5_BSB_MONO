// 인기 대회 예매 오픈 몰림을 화면과 같은 요청으로 잰다 (CRU-84)
// 준비(setup): 계정 로그인 → 구간마다 대회 한 쌍(A 크루 예매, B 개인 예매, 4종목) 등록 → 대회 A에 크루 파티 만들기
// 실행: 계정 하나 = 가상 사용자 하나. 가상 사용자가 구간을 차례로 돌며, 구간마다 맡은 역할(개인 예매·파티장·파티원·보기만)을 한다
// 실행 방법은 README.md
import http from 'k6/http';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';
import {
	API,
	CLICK_SPREAD_SECONDS,
	COURSES,
	DIRECT_LOGIN_SHARE,
	EARLY_SHARE,
	FIRST_OPEN_LEAD_SECONDS,
	LATE_SPREAD_SECONDS,
	MEMBER_PAY_FROM_SECONDS,
	MEMBER_PAY_SHARE,
	MEMBER_PAY_TO_SECONDS,
	PARTY_SIZE,
	PASSWORD,
	PRE_OPEN_SECONDS,
	SELLER_EMAIL,
	SELLER_PASSWORD,
	STAGE_GAP_SECONDS,
	THINK,
	accountLayout,
	courseIndexOf,
	emailOf,
} from './config.js';
import { PageFailed, Session, data } from './lib/session.js';
import {
	browseOnly,
	flowBroken,
	individualBooking,
	isTestEmail,
	openApp,
	partyApply,
	partyMemberPay,
	sleepUntil,
} from './lib/flows.js';

if (!PASSWORD) {
	throw new Error('LOADTEST_PASSWORD 환경 변수가 없습니다');
}

const layout = accountLayout();
// seed/seed.js가 만든 크루 목록: [{ crewId, accounts: [번호 5개, 첫 번째가 크루장] }]
const crews = new SharedArray('crews', () => JSON.parse(open(__ENV.CREWS_FILE || './.crews.json')));
if (crews.length < layout.crewCount) {
	throw new Error(`크루가 모자랍니다: ${layout.crewCount}개 필요, ${crews.length}개 있음. seed/seed.js를 다시 돌리세요`);
}
// 준비 단계에 걸리는 시간(초). 이 시간 + FIRST_OPEN_LEAD 뒤에 첫 오픈이 온다
const PREP_SECONDS = Number(__ENV.PREP || 180);

export const options = {
	setupTimeout: '30m',
	// k6는 기본으로 반복(구간)마다 쿠키 저장소를 비운다. 비우면 다음 구간에서 Refresh Token이 없어 재발급이 401이 된다
	// 사람은 구간 사이에도 로그인 상태(쿠키)를 그대로 가지고 있으므로 비우지 않는다
	noCookiesReset: true,
	scenarios: {
		rush: {
			executor: 'per-vu-iterations',
			exec: 'rush',
			vus: layout.total,
			iterations: layout.perStage.length,
			maxDuration: `${PREP_SECONDS + FIRST_OPEN_LEAD_SECONDS + STAGE_GAP_SECONDS * layout.perStage.length + 900}s`,
		},
	},
	// 통과 기준 (8절): 화면 요청(page)은 기준에서 뺀다
	// 자동멈춤: 전체 실패율 5%가 30초 넘게 이어지면 멈춘다
	thresholds: {
		'http_req_duration{kind:read}': ['p(95)<300'],
		'http_req_duration{kind:queue}': ['p(95)<200'],
		'http_req_duration{kind:write}': ['p(95)<1000'],
		'http_req_failed{kind:read}': ['rate<0.01'],
		'http_req_failed{kind:queue}': ['rate<0.01'],
		'http_req_failed{kind:write}': ['rate<0.01'],
		http_req_failed: [{ threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: '30s' }],
		pay_busy_first: ['count>=0'],
		pay_retry_success: ['count>=0'],
		booking_failed_final: ['count==0'],
		flow_broken: ['count==0'],
		charge_needed: ['count>=0'],
		charge_completed: ['count>=0'],
		charge_failed: ['count==0'],
	},
	summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
};

// ── 준비 ──────────────────────────────────────────────────────

const pad = (n) => String(n).padStart(2, '0');
// 서버(한국 시간) 기준 일시 문자열. k6를 어느 시간대에서 돌려도 같게 한다
function kst(ms) {
	const d = new Date(ms + 9 * 3600 * 1000);
	return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}T${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}:${pad(d.getUTCSeconds())}`;
}
function kstDate(ms, plusDays = 0, hh = 0, mm = 0) {
	const d = new Date(ms + 9 * 3600 * 1000 + plusDays * 86400 * 1000);
	return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}T${pad(hh)}:${pad(mm)}:00`;
}

function call(method, path, body, token, extraHeaders = {}) {
	return http.request(method, `${API}${path}`, body === null ? null : JSON.stringify(body), {
		headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...extraHeaders },
		tags: { kind: 'setup' },
	});
}

// 한꺼번에 로그인할 계정 수. 운영 회원 서비스(메모리 640Mi)가 가입 50건을 한꺼번에 받다 메모리 초과로 죽어(2026-11-27) 운영은 20으로 줄여 돌린다
const LOGIN_BATCH = Number(__ENV.LOGIN_BATCH || 50);
// 계정을 50개씩 한꺼번에 로그인한다. Access Token은 파티를 만들 때, Refresh Token은 가상 사용자가 앱을 열 때 쓴다
function loginAll(count) {
	const tokens = new Array(count + 1);
	for (let from = 1; from <= count; from += LOGIN_BATCH) {
		const nos = [];
		for (let no = from; no < Math.min(from + LOGIN_BATCH, count + 1); no++) {
			nos.push(no);
		}
		const res = http.batch(nos.map((no) => ({
			method: 'POST', url: `${API}/auth/login`,
			body: JSON.stringify({ email: emailOf(no), password: PASSWORD }),
			params: { headers: { 'Content-Type': 'application/json' }, tags: { kind: 'setup' } },
		})));
		res.forEach((r, i) => {
			if (r.status !== 200) {
				throw new Error(`${emailOf(nos[i])} 로그인 실패 ${r.status} ${r.body}`);
			}
			const cookie = r.cookies.refreshToken;
			tokens[nos[i]] = { access: r.json('data.accessToken'), refresh: cookie ? cookie[0].value : null };
		});
	}
	return tokens;
}

function registerRace(sellerToken, role, stageNo, pairNo, openAtMs, pair) {
	const crew = role === 'A';
	const quotaOf = (i) => (crew ? pair.crewQuota[i] : pair.individualQuota[i]);
	const body = {
		title: `[실흐름] ${stageNo}구간 ${pairNo + 1}쌍 ${crew ? '크루 예매 대회 A' : '개인 예매 대회 B'} ${kst(openAtMs).slice(5, 16)}`,
		description: 'real-flow 부하 테스트용 대회입니다 (CRU-84).',
		location: '여의도 한강공원', regionSido: '서울특별시', regionSigungu: '영등포구',
		raceDate: kstDate(openAtMs, 60, 8),
		crewSaleStartAt: crew ? kst(openAtMs) : null,
		crewSaleEndAt: crew ? kstDate(openAtMs, 0, 23, 59) : null,
		// 한 대회 안에서 크루 기간과 개인 기간은 겹치지 않는다 (9절): A의 개인 오픈은 크루 마감 다음 날
		individualOpenAt: crew ? kstDate(openAtMs, 1, 10) : kst(openAtMs),
		applicationCloseAt: kstDate(openAtMs, 40, 18),
		refundDeadline: kstDate(openAtMs, 40).slice(0, 10),
		refundPolicy: '환불 기한까지 취소하면 전액 환불됩니다.',
		crewMinCount: 4,
		crewMaxCount: PARTY_SIZE,
		courses: COURSES.map((c, i) => ({
			courseId: null, name: c.name, courseType: c.courseType, surface: 'ROAD', distanceKm: c.distanceKm,
			timeLimitMinutes: c.timeLimitMinutes, price: c.price, kitIncluded: false, kitPrice: 0, minAge: 14, maxAge: null,
			crewQuota: crew ? quotaOf(i) : 0, individualQuota: crew ? 0 : quotaOf(i), images: [],
		})),
		images: [{ fileId: 999999999, fileType: 'THUMBNAIL', sortNo: 0 }],
	};
	const res = call('POST', '/product/races', body, sellerToken, { 'Idempotency-Key': `rf-${stageNo}-${pairNo}-${role}-${openAtMs}` });
	if (res.status !== 201) {
		throw new Error(`대회 ${role} 등록 실패 ${res.status} ${res.body}`);
	}
	return { raceId: res.json('data.raceId'), courses: res.json('data.courseIds').map((id) => ({ raceCourseId: id })) };
}

const participant = (name) => ({
	name, birth: '1995-01-01', gender: 'MALE', phone: '010-1234-5678',
	emergencyContactName: '보호자', emergencyContactPhone: '010-8765-4321',
});

// 크루 파티를 만든다: 파티장이 만들고 나머지 4명이 참가 링크로 합류 (화면: 파티 만들기·파티 합류)
// 파티는 오픈 전에 미리 만들어 둔다 (시트 크루 시나리오: 파티가 미리 만들어져 있다고 가정)
function makeParties(tokens, list) {
	const parties = [];
	for (let from = 0; from < list.length; from += 50) {
		const chunk = list.slice(from, from + 50);
		const created = http.batch(chunk.map((p) => ({
			method: 'POST', url: `${API}/order/parties`,
			body: JSON.stringify({ crewId: p.crewId, raceCourseId: p.raceCourseId, participant: participant('파티장'), thirdPartyConsent: true }),
			params: { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${tokens[p.leader].access}` }, tags: { kind: 'setup' } },
		})));
		const joins = [];
		created.forEach((r, i) => {
			if (r.status !== 201) {
				throw new Error(`파티 만들기 실패 (크루 ${chunk[i].crewId}) ${r.status} ${r.body}`);
			}
			chunk[i].partyId = r.json('data.party.id');
			const joinToken = r.json('data.party.joinToken');
			for (const no of chunk[i].members) {
				joins.push({
					method: 'POST', url: `${API}/order/parties/join`,
					body: JSON.stringify({ joinToken, participant: participant('파티원'), thirdPartyConsent: true }),
					params: { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${tokens[no].access}` }, tags: { kind: 'setup' } },
				});
			}
		});
		http.batch(joins).forEach((r) => {
			if (r.status !== 201) {
				throw new Error(`파티 합류 실패 ${r.status} ${r.body}`);
			}
		});
		parties.push(...chunk);
	}
	return parties;
}

export function setup() {
	const t0 = Date.now();
	const tokens = loginAll(layout.total);
	const sellerLogin = call('POST', '/auth/login', { email: SELLER_EMAIL, password: SELLER_PASSWORD });
	if (sellerLogin.status !== 200) {
		throw new Error(`판매자 로그인 실패 ${sellerLogin.status}`);
	}
	const sellerToken = sellerLogin.json('data.accessToken');

	// 오픈 시각: 첫 구간은 준비 시간 뒤, 다음 구간부터는 STAGE_GAP마다 (12절)
	const firstOpen = Math.ceil((Date.now() + (PREP_SECONDS + FIRST_OPEN_LEAD_SECONDS) * 1000) / 60000) * 60000;
	const stages = layout.perStage.map((st, s) => {
		const openAt = firstOpen + s * STAGE_GAP_SECONDS * 1000;
		const pairs = [];
		for (let p = 0; p < st.pairs; p++) {
			pairs.push({ A: registerRace(sellerToken, 'A', s + 1, p, openAt, st.pair), B: registerRace(sellerToken, 'B', s + 1, p, openAt, st.pair) });
		}
		return { n: st.n, pairs: st.pairs, openAt, pairList: pairs, parties: [] };
	});

	// 파티: 구간 s는 짝수·홀수 크루 묶음 중 하나를 쓴다
	stages.forEach((st, s) => {
		const plan = layout.perStage[s].pair;
		const list = [];
		for (let j = 0; j < layout.perStage[s].parties; j++) {
			const crewIndex = (s % 2 === 0 ? 0 : layout.crewsEven) + j;
			const crew = crews[crewIndex];
			const pairNo = Math.floor(j / plan.parties);
			const c = courseIndexOf(j % plan.parties, plan.partyByCourse);
			list.push({
				crewIndex, crewId: crew.crewId, leader: crew.accounts[0], members: crew.accounts.slice(1),
				pairNo, courseIndex: c, raceCourseId: st.pairList[pairNo].A.courses[c].raceCourseId,
			});
		}
		st.parties = makeParties(tokens, list);
	});

	const lead = (stages[0].openAt - Date.now()) / 1000;
	if (lead < PRE_OPEN_SECONDS + 10) {
		throw new Error(`준비가 늦어 첫 오픈까지 ${Math.floor(lead)}초밖에 없습니다. -e PREP=${PREP_SECONDS + 120}처럼 늘리세요`);
	}
	console.log(`준비 끝 (${Math.round((Date.now() - t0) / 1000)}초). 첫 오픈 ${kst(stages[0].openAt)}, 구간 ${stages.map((s) => s.n + 'x' + s.pairs).join(', ')}`);
	console.log(JSON.stringify(stages.map((s) => ({ n: s.n, openAt: kst(s.openAt), races: s.pairList.map((p) => [p.A.raceId, p.B.raceId]) }))));
	return { stages, refresh: tokens.map((t) => (t ? t.refresh : null)) };
}

// ── 실행 ──────────────────────────────────────────────────────

// 가상 사용자·구간마다 같은 값이 나오는 0~1 난수 (다시 돌려도 같은 사람이 같은 행동을 한다)
function rand(vu, stage, salt) {
	let h = (vu * 73856093) ^ (stage * 19349663) ^ (salt * 83492791);
	h = Math.imul(h ^ (h >>> 16), 2246822507);
	h = Math.imul(h ^ (h >>> 13), 3266489909);
	return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
}

// 이 가상 사용자가 이 구간에서 맡는 역할
function roleOf(no, s) {
	const st = layout.perStage[s];
	const plan = st.pair;
	if (no <= layout.crewAccounts) {
		const crewIndex = Math.floor((no - 1) / PARTY_SIZE);
		const even = crewIndex < layout.crewsEven;
		if (even !== (s % 2 === 0)) {
			return null;
		}
		const j = even ? crewIndex : crewIndex - layout.crewsEven;
		if (j >= st.parties) {
			return null;
		}
		const leader = (no - 1) % PARTY_SIZE === 0;
		return { type: leader ? 'leader' : 'member', partyIndex: j };
	}
	const k = no - layout.crewAccounts - 1;
	if (k < layout.maxIndividuals) {
		return k < st.individuals
			? { type: 'individual', pairNo: Math.floor(k / plan.individuals), courseIndex: courseIndexOf(k % plan.individuals, plan.individualByCourse) }
			: null;
	}
	const b = k - layout.maxIndividuals;
	return b < st.browse ? { type: 'browse', pairNo: Math.floor(b / plan.browse) } : null;
}

// 가상 사용자마다 따로 있는 값 (k6는 가상 사용자마다 이 파일을 따로 읽는다)
let usedSetupToken = false;
// 지금 구간에서 맡은 역할의 지표 태그 (화면이 끝내 안 떠 흐름이 끊겼을 때 센다)
let currentTags = null;

export function rush(setupData) {
	currentTags = null;
	try {
		play(setupData);
	} catch (e) {
		if (!(e instanceof PageFailed) || !currentTags) {
			throw e;
		}
		flowBroken.add(1, { ...currentTags, where: '화면' });
	}
}

function play(setupData) {
	const no = exec.vu.idInTest;
	const s = exec.vu.iterationInScenario;
	const role = roleOf(no, s);
	if (!role) {
		return;
	}
	const stage = setupData.stages[s];
	const email = emailOf(no);
	if (!isTestEmail(email)) {
		throw new Error(`테스트 계정이 아닙니다: ${email}`);
	}
	exec.vu.metrics.tags.stage = `${stage.n}x${stage.pairs}`;
	const metricTags = { stage: `${stage.n}x${stage.pairs}`, flow: role.type };
	currentTags = metricTags;
	const session = new Session(role.type);
	// 처음 나서는 구간에만 준비 단계의 Refresh Token을 넣는다. 그 뒤로는 쿠키 저장소에 재발급으로 바뀐 토큰이 있다
	if (!usedSetupToken) {
		session.useRefreshToken(setupData.refresh[no]);
		usedSetupToken = true;
	}

	const T = stage.openAt;
	const early = rand(no, s, 1) < EARLY_SHARE;
	const arriveAt = early
		? T - PRE_OPEN_SECONDS * 1000 + rand(no, s, 2) * PRE_OPEN_SECONDS * 1000
		: T + rand(no, s, 2) * LATE_SPREAD_SECONDS * 1000;
	const clickAt = early ? T + rand(no, s, 3) * CLICK_SPREAD_SECONDS * 1000 : arriveAt + THINK.lateDetailToApply * 1000;
	const directLogin = rand(no, s, 4) < DIRECT_LOGIN_SHARE;

	if (role.type === 'member') {
		// 파티원: 25%만 알림 뒤 1~5분 사이 들어와 결제한다 (9-2)
		if (rand(no, s, 5) >= MEMBER_PAY_SHARE) {
			return;
		}
		const party = stage.parties[role.partyIndex];
		sleepUntil(T + (MEMBER_PAY_FROM_SECONDS + rand(no, s, 6) * (MEMBER_PAY_TO_SECONDS - MEMBER_PAY_FROM_SECONDS)) * 1000);
		if (!openApp(session, { email, directLogin })) {
			flowBroken.add(1, { ...metricTags, where: '앱 열기' });
			return;
		}
		const race = stage.pairList[party.pairNo].A;
		partyMemberPay(session, {
			race, course: race.courses[party.courseIndex], crewId: party.crewId, partyId: party.partyId,
			giveUpAt: T + (MEMBER_PAY_TO_SECONDS + 180) * 1000, metricTags,
		});
		return;
	}

	sleepUntil(arriveAt);
	// 앱 열기(재발급·로그인)가 안 되면 그 사람은 아무것도 못 한다. 조용히 끝내지 않고 흐름 끊김으로 센다
	if (!openApp(session, { email, directLogin })) {
		flowBroken.add(1, { ...metricTags, where: '앱 열기' });
		return;
	}
	if (role.type === 'browse') {
		const pair = stage.pairList[role.pairNo];
		browseOnly(session, rand(no, s, 7) < 0.5 ? pair.A : pair.B);
	} else if (role.type === 'individual') {
		const race = stage.pairList[role.pairNo].B;
		individualBooking(session, { race, course: race.courses[role.courseIndex], clickAt, metricTags });
	} else {
		const party = stage.parties[role.partyIndex];
		const race = stage.pairList[party.pairNo].A;
		partyApply(session, {
			race, course: race.courses[party.courseIndex], crewId: party.crewId, partyId: party.partyId, clickAt, metricTags,
		});
	}
}

export function handleSummary(summary) {
	const out = __ENV.SUMMARY_FILE || 'results/real-flow-summary.json';
	const m = summary.metrics;
	const count = (name) => (m[name] ? m[name].values.count : 0);
	const text = [
		'',
		`결제까지 끝난 예매 booking_completed: ${count('booking_completed')}`,
		`매진 booking_sold_out: ${count('booking_sold_out')}`,
		`첫 결제 503 pay_busy_first: ${count('pay_busy_first')}  다시 시도해 결제 pay_retry_success: ${count('pay_retry_success')}`,
		`결제 못 함 booking_failed_final: ${count('booking_failed_final')}`,
		`파티 신청 성공 party_applied: ${count('party_applied')}  크루 자리 부족 party_seat_short: ${count('party_seat_short')}`,
		`충전하러 감 charge_needed: ${count('charge_needed')}  충전됨 charge_completed: ${count('charge_completed')}  충전 실패 charge_failed: ${count('charge_failed')}`,
		`흐름 끊김 flow_broken: ${count('flow_broken')}`,
		'',
	].join('\n');
	return { stdout: text, [out]: JSON.stringify(summary, null, 2) };
}
