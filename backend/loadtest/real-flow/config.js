// real-flow 설정과 인원 계산 (CRU-84)
// 정한 값의 근거는 「부하 시나리오 작성 팁」 문서(2026-11-19)와 구글 시트 「부하 시나리오」 탭이다. 절 번호를 주석에 적는다

// 화면 주소(Next)와 API 주소. 운영은 둘 다 https://crewrun.duckdns.org (nginx가 /api만 게이트웨이로 보낸다)
// 로컬은 화면 3177(next dev), API 8000(게이트웨이)으로 나눠 보낸다. next dev가 /api를 다시 넘기며 생기는 부하를 빼려는 것이다
export const BASE_URL = __ENV.BASE_URL || 'https://crewrun.duckdns.org';
export const API_URL = __ENV.API_URL || BASE_URL;
export const API = `${API_URL}/api/v1`;
// 화면 요청(_rsc)을 빼고 API만 보내려면 PAGES=0 (Next 서버 부하를 따로 보고 싶을 때)
export const SEND_PAGES = __ENV.PAGES !== '0';

// 테스트 계정: realflow{번호}@crewrun.test, 비밀번호는 실행할 때 환경 변수로 받는다 (seed/seed.js가 만든다)
export const EMAIL_PREFIX = __ENV.EMAIL_PREFIX || 'realflow';
export const EMAIL_DOMAIN = __ENV.EMAIL_DOMAIN || 'crewrun.test';
export const PASSWORD = __ENV.LOADTEST_PASSWORD;
export function emailOf(no) {
	return `${EMAIL_PREFIX}${no}@${EMAIL_DOMAIN}`;
}

// 판매자 계정 (대회를 등록한다). 로컬은 시드 판매자 seller1@crewrun.com
export const SELLER_EMAIL = __ENV.SELLER_EMAIL || 'seller1@crewrun.com';
export const SELLER_PASSWORD = __ENV.SELLER_PASSWORD || PASSWORD;

// ── 사람 나누기 ──────────────────────────────────────────────
// 10-3: 몰린 인원 N을 개인 예매(대회 B) 80% : 크루 파티장(대회 A) 20%. 파티 5명
export const INDIVIDUAL_SHARE = 0.8;
export const LEADER_SHARE = 0.2;
export const PARTY_SIZE = 5;
// 10-2: 종목 나누기 (두 대회 모두). 순서는 등록하는 종목 순서와 같다
export const COURSES = [
	{ key: '10K', name: '10K', courseType: 'KM_10', distanceKm: 10, timeLimitMinutes: 90, price: 40000, share: 0.4 },
	{ key: 'HALF', name: '하프', courseType: 'HALF', distanceKm: 21.1, timeLimitMinutes: 180, price: 50000, share: 0.3 },
	{ key: '5K', name: '5K', courseType: 'KM_5', distanceKm: 5, timeLimitMinutes: 60, price: 30000, share: 0.2 },
	{ key: 'FULL', name: '풀', courseType: 'FULL', distanceKm: 42.2, timeLimitMinutes: 360, price: 60000, share: 0.1 },
];
// 14: 정원 = 몰리는 인원의 70%. 크루 정원은 들어갈 수 있는 파티 수 × 5
export const QUOTA_SHARE = 0.7;
// 14: 보기만 하는 사람 = N의 10%, N에 들어가지 않는다
export const BROWSE_SHARE = 0.1;
// 15: 앱을 여는 사람 중 10%는 쿠키가 없어 직접 로그인한다
export const DIRECT_LOGIN_SHARE = 0.1;
// 9-2: 정원 안에 든 파티의 파티원 25%가 알림 뒤 1~5분 사이 결제한다
export const MEMBER_PAY_SHARE = 0.25;
export const MEMBER_PAY_FROM_SECONDS = 60;
export const MEMBER_PAY_TO_SECONDS = 300;

// ── 시간 ─────────────────────────────────────────────────────
// 11: 90%가 오픈 1분 전부터 들어와 기다리다 정각 5초 안에 누르고, 10%는 오픈 뒤 1분 동안 들어온다
export const EARLY_SHARE = 0.9;
export const PRE_OPEN_SECONDS = 60;
export const CLICK_SPREAD_SECONDS = 5;
export const LATE_SPREAD_SECONDS = 60;
// 13: 생각하는 시간
export const THINK = {
	lateDetailToApply: 5, // 늦게 온 사람이 상세를 보고 「신청하기」를 누르기까지
	form: 30, // 참가 신청서 작성
	pay: 5, // 결제 화면에서 가격 확인
	failScreen: 3, // 결제 실패 화면을 읽고 「다시 결제하기」
	retryPay: 2, // 다시 열린 결제 화면에서 결제를 다시 누르기까지
	browse: 3, // 보기만 하는 사람이 화면을 보는 시간
};
// 화면이 순서·입장을 다시 묻는 주기 (frontend ADMISSION_POLL_MS)
export const POLL_SECONDS = 3;
// 8-3: 결제 503이면 최대 5번까지 누른다
export const MAX_PAY_ATTEMPTS = 5;

// ── 구간 ─────────────────────────────────────────────────────
// 12: N·M 실행은 100 → 300 → 600 → 1,000 (대회 한 쌍). X 실행은 10-1·12-2처럼 쌍을 늘린다: STAGES=600x1,600x2,600x3
// 다음 구간은 앞 구간의 몰림이 끝나면 바로 시작한다. 오픈 사이 간격(초): 몰림 약 2.5분 + 다음 구간 오픈 전 대기 1분
export const STAGES = parseStages(__ENV.STAGES || '100,300,600,1000');
export const STAGE_GAP_SECONDS = Number(__ENV.STAGE_GAP || 240);
// setup이 끝난 뒤 첫 오픈까지 (첫 구간의 오픈 전 대기 1분이 들어가야 한다)
export const FIRST_OPEN_LEAD_SECONDS = Number(__ENV.FIRST_OPEN_LEAD || 90);

function parseStages(text) {
	return text.split(',').map((part) => {
		const [n, pairs] = part.trim().split('x');
		return { n: Number(n), pairs: Number(pairs || 1) };
	});
}

// ── 인원 계산 (12-2) ─────────────────────────────────────────
// 반올림으로 생기는 끝수는 큰 종목부터 채워 합이 맞게 한다
function splitByCourse(total) {
	const counts = COURSES.map((c) => Math.floor(total * c.share));
	let left = total - counts.reduce((a, b) => a + b, 0);
	for (let i = 0; left > 0; i = (i + 1) % counts.length, left--) {
		counts[i]++;
	}
	return counts;
}

// 대회 한 쌍의 인원과 정원
export function planPair(n) {
	const individuals = Math.round(n * INDIVIDUAL_SHARE);
	const parties = n - individuals;
	const individualByCourse = splitByCourse(individuals);
	const partyByCourse = splitByCourse(parties);
	return {
		n,
		individuals,
		parties,
		browse: Math.round(n * BROWSE_SHARE),
		individualByCourse,
		partyByCourse,
		individualQuota: individualByCourse.map((c) => Math.round(c * QUOTA_SHARE)),
		crewQuota: partyByCourse.map((c) => Math.round(c * QUOTA_SHARE) * PARTY_SIZE),
	};
}

// 순번 i(0부터)를 종목에 배정한다. 앞 순번부터 큰 종목을 채운다
export function courseIndexOf(i, byCourse) {
	let acc = 0;
	for (let c = 0; c < byCourse.length; c++) {
		acc += byCourse[c];
		if (i < acc) {
			return c;
		}
	}
	return byCourse.length - 1;
}

// ── 계정 배치 ────────────────────────────────────────────────
// 계정 하나는 한 가상 사용자가 끝까지 쓴다 (재발급이 Refresh Token을 바꾸기 때문. member TokenReissueUseCase)
// 개인 예매·보기만 계정은 구간마다 다시 쓴다(새 대회라 중복 신청이 아니다)
// 크루는 짝수·홀수 구간이 다른 묶음을 쓴다. 앞 구간 파티원 결제(오픈 뒤 최대 약 6분)가 다음 구간과 겹치기 때문이다
export function accountLayout() {
	const perStage = STAGES.map((s) => {
		const pair = planPair(s.n);
		return { ...s, pair, individuals: pair.individuals * s.pairs, browse: pair.browse * s.pairs, parties: pair.parties * s.pairs };
	});
	const maxIndividuals = Math.max(...perStage.map((s) => s.individuals));
	const maxBrowse = Math.max(...perStage.map((s) => s.browse));
	const crewsEven = Math.max(0, ...perStage.filter((_, i) => i % 2 === 0).map((s) => s.parties));
	const crewsOdd = Math.max(0, ...perStage.filter((_, i) => i % 2 === 1).map((s) => s.parties));
	return {
		perStage,
		maxIndividuals,
		maxBrowse,
		crewsEven,
		crewsOdd,
		crewCount: crewsEven + crewsOdd,
		// 계정 번호: 1부터 크루 계정(크루 수 × 5) → 개인 예매 → 보기만
		crewAccounts: (crewsEven + crewsOdd) * PARTY_SIZE,
		total: (crewsEven + crewsOdd) * PARTY_SIZE + maxIndividuals + maxBrowse,
	};
}
