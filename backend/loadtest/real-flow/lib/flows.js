// 시나리오 5개. 요청 순서는 구글 시트 「부하 시나리오」 탭(로컬 화면을 눌러 개발자 도구로 확인한 것)을 그대로 옮겼다
// 각 함수 위 주석의 번호는 시트의 시나리오 번호다
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import {
	EMAIL_DOMAIN,
	MAX_PAY_ATTEMPTS,
	PASSWORD,
	POLL_SECONDS,
	THINK,
} from '../config.js';
import { code, data } from './session.js';

// ── 사람 단위로 세는 숫자 (8-4) ──────────────────────────────
export const bookingCompleted = new Counter('booking_completed'); // 결제까지 끝난 예매 (TPS 기준)
export const bookingSoldOut = new Counter('booking_sold_out'); // 주문에서 매진(409)
export const bookingFailedFinal = new Counter('booking_failed_final'); // 다시 시도해도 결제 못 함
export const payBusyFirst = new Counter('pay_busy_first'); // 첫 결제에서 503
export const payRetrySuccess = new Counter('pay_retry_success'); // 다시 시도해 결국 결제됨
export const payAttempts = new Trend('pay_attempts'); // 한 사람이 결제를 누른 횟수
export const partyApplied = new Counter('party_applied'); // 파티 신청 성공 (크루 자리 확보)
export const partySeatShort = new Counter('party_seat_short'); // 크루 자리가 모자라 신청 거절
export const flowBroken = new Counter('flow_broken'); // 예상하지 못한 응답으로 흐름이 끊김

const SEC = 1000;

// 정해진 시각(ms)까지 기다린다
export function sleepUntil(atMs) {
	const wait = (atMs - Date.now()) / SEC;
	if (wait > 0) {
		sleep(wait);
	}
}

function broken(tagsForMetric, where, res) {
	flowBroken.add(1, { ...tagsForMetric, where });
	check(res, { [`${where} 응답`]: () => false });
}

// ── 1. 앱 열기 (몰린 사람 전원) / 1-1. 직접 로그인 (10%) ───────
// 앱을 새로 열면 재발급으로 로그인을 되살리고, 홈이 내 정보·알림·내 주문을 부른다
export function openApp(s, { email, directLogin }) {
	s.page('/', 'PAGE /', { first: true });
	if (directLogin) {
		// 쿠키가 없는 사람: 재발급 401 → 로그인 화면 → 로그인
		s.forgetCookies();
		s.reissue();
		s.page('/login?next=/', 'PAGE /login');
		s.api('GET', '/auth/demo-login', { name: 'GET /auth/demo-login', expected: [200, 404] });
		const login = s.login(email, PASSWORD);
		if (login.status !== 200) {
			return false;
		}
		s.page('/', 'PAGE /');
	} else if (s.reissue().status !== 200) {
		return false;
	}
	s.api('GET', '/member/members/me', { name: 'GET /member/members/me' });
	s.api('GET', '/notification/notifications', { name: 'GET /notification/notifications' });
	homeTickets(s);
	return true;
}

// 홈의 다음 대회 카드: 내 주문 목록 → 주문마다 상세 → 대회마다 정보 → 앞 2개 대회 이미지
function homeTickets(s) {
	const list = s.api('GET', '/order/orders?page=0&size=100', { name: 'GET /order/orders?page' });
	const orders = data(list, 'data.orders') || [];
	const raceIds = [];
	for (const o of orders) {
		const detail = s.api('GET', `/order/orders/${o.id}`, { name: 'GET /order/orders/{orderId}' });
		const raceId = data(detail, 'data.raceId');
		if (raceId && !raceIds.includes(raceId)) {
			raceIds.push(raceId);
		}
	}
	for (const raceId of raceIds) {
		s.api('GET', `/product/races/${raceId}`, { name: 'GET /product/races/{raceId}' });
	}
	for (const raceId of raceIds.slice(0, 2)) {
		s.api('GET', `/file/files?refType=Race&refId=${raceId}`, { name: 'GET /file/files?refType=Race' });
	}
}

// 대회 목록 → 상세 (2·3·5번 공통). 목록·상세 정보는 Next 서버가 화면을 그리며 부른다
function listAndDetail(s, raceId, { partyStatus = false } = {}) {
	s.page('/races', 'PAGE /races');
	s.page(`/races/${raceId}`, 'PAGE /races/{raceId}');
	s.api('GET', `/product/races/${raceId}/similar`, { name: 'GET /product/races/{raceId}/similar' });
	if (partyStatus) {
		s.api('GET', `/order/parties/me/race-status?raceId=${raceId}`, { name: 'GET /order/parties/me/race-status' });
	}
}

// ── 5. 보기만 하는 사람 (N의 10%) ──────────────────────────────
export function browseOnly(s, race) {
	listAndDetail(s, race.raceId);
	sleep(THINK.browse);
}

// ── 결제 (2·4번 공통). 8-3: 503이면 실패 화면 → 「다시 결제하기」 → 다시 누름, 최대 5번 ──
function pay(s, { orderId, participationId, raceId, raceCourseId, metricTags }) {
	const admission = `/order/queue/admission?raceId=${raceId}&raceCourseId=${raceCourseId}`;
	for (let attempt = 1; attempt <= MAX_PAY_ATTEMPTS; attempt++) {
		const res = s.api('POST', `/order/orders/${orderId}/payment`, {
			name: 'POST /order/orders/{orderId}/payment', kind: 'write', expected: [200, 202, 503],
		});
		let paid = res.status === 200 && data(res, 'data.status') === 'PAID';
		if (res.status === 202) {
			// 결제 확인 중: 화면이 주문을 3초마다 다시 본다. 결제 대기로 돌아오면 거절된 것
			for (let i = 0; i < 40; i++) {
				sleep(POLL_SECONDS);
				const st = data(s.api('GET', `/order/orders/${orderId}`, { name: 'GET /order/orders/{orderId}' }), 'data.status');
				if (st === 'PAID') {
					paid = true;
					break;
				}
				if (st !== 'PAYING') {
					break;
				}
			}
		}
		if (paid) {
			payAttempts.add(attempt, metricTags);
			bookingCompleted.add(1, metricTags);
			if (attempt > 1) {
				payRetrySuccess.add(1, metricTags);
			}
			// 결제 뒤 화면 값 다시 조회 → 완료 화면
			s.api('GET', admission, { name: 'GET /order/queue/admission', kind: 'queue' });
			s.api('GET', '/cash/wallets/balance', { name: 'GET /cash/wallets/balance' });
			s.api('GET', `/order/participations/${participationId}`, { name: 'GET /order/participations/{id}' });
			s.page(`/checkout/complete?orderId=${orderId}`, 'PAGE /checkout/complete');
			s.api('GET', admission, { name: 'GET /order/queue/admission', kind: 'queue' });
			s.api('GET', '/cash/wallets/balance', { name: 'GET /cash/wallets/balance' });
			s.api('GET', `/order/participations/${participationId}`, { name: 'GET /order/participations/{id}' });
			return true;
		}
		if (attempt === 1 && res.status === 503) {
			payBusyFirst.add(1, metricTags);
		}
		if (res.status !== 503 && res.status !== 202) {
			broken(metricTags, 'payment', res);
			break;
		}
		// 실패 화면(M07-3)을 읽고 「다시 결제하기」 → 주문 다시 조회 → 결제 화면에서 다시 누름
		sleep(THINK.failScreen);
		s.api('GET', `/order/orders/${orderId}`, { name: 'GET /order/orders/{orderId}' });
		sleep(THINK.retryPay);
	}
	payAttempts.add(MAX_PAY_ATTEMPTS, metricTags);
	bookingFailedFinal.add(1, metricTags);
	return false;
}

// 대기열 줄서기와 입장 확인 (개인). 409(이미 입장)면 순서 조회 없이 입장 확인으로 간다
function waitIndividualQueue(s, raceId, raceCourseId, metricTags) {
	const body = { raceId, raceCourseId };
	const register = () => s.api('POST', '/queue', { body, name: 'POST /queue', kind: 'queue', expected: [200, 409] });
	const again = register();
	let canEnter = again.status === 409;
	let lost = 0;
	const deadline = Date.now() + 15 * 60 * SEC;
	while (!canEnter && Date.now() < deadline) {
		const res = s.api('GET', `/queue/races/${raceId}/racecourses/${raceCourseId}`, {
			name: 'GET /queue/races/{raceId}/racecourses/{raceCourseId}', kind: 'queue', expected: [200, 404],
		});
		if (res.status === 404) {
			// 줄에서 사라짐: 다시 등록 (최대 2번)
			if (++lost > 2) {
				break;
			}
			register();
			continue;
		}
		canEnter = data(res, 'data.canEnter') === true;
		if (!canEnter) {
			sleep(POLL_SECONDS);
		}
	}
	if (!canEnter) {
		broken(metricTags, 'queue', again);
		return false;
	}
	const admission = `/order/queue/admission?raceId=${raceId}&raceCourseId=${raceCourseId}`;
	for (let i = 0; i < 20; i++) {
		const res = s.api('GET', admission, { name: 'GET /order/queue/admission', kind: 'queue' });
		if (data(res, 'data.admitted') === true) {
			return true;
		}
		sleep(POLL_SECONDS);
	}
	return false;
}

// ── 2. 개인 예매 (대회 B) ──────────────────────────────────────
// clickAt: 「신청하기」를 누르는 시각(ms). 오픈 전이면 그때까지 상세 화면에서 기다린다
export function individualBooking(s, { race, course, clickAt, metricTags }) {
	const { raceId } = race;
	const raceCourseId = course.raceCourseId;
	listAndDetail(s, raceId);
	sleepUntil(clickAt);

	// 상세의 「신청하기」: 줄에 등록. 시계 차이로 아직 열리지 않았으면(403) 1초 뒤 다시 누른다
	let first;
	for (let i = 0; i < 10; i++) {
		first = s.api('POST', '/queue', {
			body: { raceId, raceCourseId }, name: 'POST /queue', kind: 'queue', expected: [200, 403, 409],
		});
		if (first.status !== 403) {
			break;
		}
		sleep(1);
	}
	if (first.status === 403) {
		broken(metricTags, 'queue-open', first);
		return;
	}

	// 대기열 화면: 다시 등록 + 대회·종목 이미지 + 순서 조회 + 입장 확인
	s.page(`/races/${raceId}/queue?option=${raceCourseId}`, 'PAGE /races/{raceId}/queue');
	s.api('GET', `/file/files?refType=Race&refId=${raceId}`, { name: 'GET /file/files?refType=Race' });
	for (const c of race.courses) {
		s.api('GET', `/file/files?refType=RaceCourse&refId=${c.raceCourseId}`, { name: 'GET /file/files?refType=RaceCourse' });
	}
	if (!waitIndividualQueue(s, raceId, raceCourseId, metricTags)) {
		return;
	}

	// 참가 신청서
	s.page(`/races/${raceId}/apply?option=${raceCourseId}`, 'PAGE /races/{raceId}/apply');
	s.api('GET', '/order/participations/latest-participant', { name: 'GET /order/participations/latest-participant', expected: [200, 404] });
	s.api('GET', `/product/races/${raceId}/kit-options`, { name: 'GET /product/races/{raceId}/kit-options' });
	s.api('GET', '/order/participations/me?active=true&size=50', { name: 'GET /order/participations/me' });
	sleep(THINK.form);

	const applied = s.api('POST', '/order/participations', {
		name: 'POST /order/participations', kind: 'write', expected: [201],
		body: {
			raceCourseId,
			thirdPartyConsent: true,
			participant: {
				name: '실흐름테스트', birth: '1995-01-01', gender: 'MALE', phone: '010-1234-5678',
				emergencyContactName: '보호자', emergencyContactPhone: '010-8765-4321',
			},
		},
	});
	const participationId = data(applied, 'data.id');
	if (applied.status !== 201 || !participationId) {
		broken(metricTags, 'participation', applied);
		return;
	}
	const ordered = s.api('POST', '/order/orders', {
		name: 'POST /order/orders', kind: 'write', expected: [200, 201, 409],
		body: { participationId },
		headers: { 'Idempotency-Key': `rf-${participationId}` },
	});
	if (ordered.status === 409) {
		if (code(ordered) === 'ORDER_SOLD_OUT') {
			bookingSoldOut.add(1, metricTags);
		} else {
			broken(metricTags, 'order', ordered);
		}
		return;
	}
	const orderId = data(ordered, 'data.id');
	if (!orderId) {
		broken(metricTags, 'order', ordered);
		return;
	}

	// 결제 화면
	s.page(`/checkout?orderId=${orderId}`, 'PAGE /checkout');
	s.api('GET', '/cash/wallets/balance', { name: 'GET /cash/wallets/balance' });
	s.api('GET', `/order/participations/${participationId}`, { name: 'GET /order/participations/{id}' });
	sleep(THINK.pay);
	pay(s, { orderId, participationId, raceId, raceCourseId, metricTags });
}

// ── 3. 크루 파티 신청 (대회 A, 파티장) ─────────────────────────
// 상세 버튼이 막혀 있어(시트 버그 6) 크루 탭 → 크루 홈 → 파티로 들어간다
export function partyApply(s, { race, course, crewId, partyId, clickAt, metricTags }) {
	const { raceId } = race;
	const raceCourseId = course.raceCourseId;
	listAndDetail(s, raceId, { partyStatus: true });
	s.page('/crews', 'PAGE /crews');
	s.api('GET', '/member/crews/me', { name: 'GET /member/crews/me' });
	crewHome(s, crewId);
	s.page(`/parties/${partyId}`, 'PAGE /parties/{partyId}');
	s.api('GET', `/order/parties/${partyId}`, { name: 'GET /order/parties/{partyId}' });
	s.page(`/parties/${partyId}/apply`, 'PAGE /parties/{partyId}/apply');
	sleepUntil(clickAt);

	// 「5명 파티 신청하기」: 파티 줄 등록 → 3초마다 순서 → 입장되면 파티 신청
	let reg;
	for (let i = 0; i < 10; i++) {
		reg = s.api('POST', '/queue/parties', {
			body: { raceId, raceCourseId }, name: 'POST /queue/parties', kind: 'queue', expected: [200, 403, 409],
		});
		if (reg.status !== 403) {
			break;
		}
		sleep(1);
	}
	if (reg.status === 403) {
		broken(metricTags, 'party-queue-open', reg);
		return;
	}
	let canEnter = reg.status === 409;
	const deadline = Date.now() + 15 * 60 * SEC;
	while (!canEnter && Date.now() < deadline) {
		const res = s.api('GET', `/queue/parties/races/${raceId}/racecourses/${raceCourseId}`, {
			name: 'GET /queue/parties/races/{raceId}/racecourses/{raceCourseId}', kind: 'queue', expected: [200, 404],
		});
		canEnter = data(res, 'data.canEnter') === true;
		if (!canEnter) {
			sleep(POLL_SECONDS);
		}
	}
	if (!canEnter) {
		broken(metricTags, 'party-queue', reg);
		return;
	}
	const applied = s.api('POST', `/order/parties/${partyId}/apply`, {
		name: 'POST /order/parties/{partyId}/apply', kind: 'write', expected: [200, 409],
	});
	if (applied.status === 409) {
		if (code(applied) === 'ORDER_PARTY_CREW_SEAT_SHORTAGE') {
			partySeatShort.add(1, metricTags);
		} else {
			broken(metricTags, 'party-apply', applied);
		}
		return;
	}
	partyApplied.add(1, metricTags);
	s.api('GET', `/order/parties/${partyId}`, { name: 'GET /order/parties/{partyId}' });
	s.api('GET', `/queue/parties/races/${raceId}/racecourses/${raceCourseId}`, {
		name: 'GET /queue/parties/races/{raceId}/racecourses/{raceCourseId}', kind: 'queue', expected: [200, 404],
	});
	s.page(`/parties/${partyId}`, 'PAGE /parties/{partyId}');
	s.api('GET', `/order/parties/me/race-status?raceId=${raceId}`, { name: 'GET /order/parties/me/race-status' });
}

function crewHome(s, crewId) {
	s.page(`/crews/${crewId}`, 'PAGE /crews/{crewId}');
	s.api('GET', `/member/crews/${crewId}`, { name: 'GET /member/crews/{crewId}' });
	const active = s.api('GET', `/order/parties/crews/${crewId}/active`, { name: 'GET /order/parties/crews/{crewId}/active' });
	for (const p of data(active, 'data') || []) {
		s.api('GET', `/product/races/${p.raceId}`, { name: 'GET /product/races/{raceId}' });
	}
}

// ── 4. 파티원 결제 (배경) ──────────────────────────────────────
// 알림을 받고 들어온 파티원: 알림 → 결제 알림 분류(안 읽은 알림을 하나씩 읽음) → 파티 → 내 몫 주문 → 결제
// 파티 신청이 아직이면(알림이 아직 안 온 것) 15초 뒤 다시 본다. giveUpAt이 지나면 그만둔다
export function partyMemberPay(s, { race, course, crewId, partyId, giveUpAt, metricTags }) {
	const { raceId } = race;
	const raceCourseId = course.raceCourseId;
	s.page('/notifications', 'PAGE /notifications');
	s.api('GET', '/member/sellers/me', { name: 'GET /member/sellers/me', expected: [200, 404] });
	s.page('/notifications/payment', 'PAGE /notifications/payment');
	const list = s.api('GET', '/notification/notifications', { name: 'GET /notification/notifications' });
	for (const n of data(list, 'data') || []) {
		if (n.unread && /PAYMENT|ORDER|CASH|REFUND/.test(n.kind || '')) {
			s.api('POST', `/notification/notifications/${n.id}/read`, { name: 'POST /notification/notifications/{id}/read', kind: 'write' });
		}
	}

	let status;
	while (Date.now() < giveUpAt) {
		s.page(`/parties/${partyId}`, 'PAGE /parties/{partyId}');
		s.api('GET', `/order/parties/${partyId}`, { name: 'GET /order/parties/{partyId}' });
		status = s.api('GET', `/order/parties/me/race-status?raceId=${raceId}`, { name: 'GET /order/parties/me/race-status' });
		if (data(status, 'data.partyStatus') !== 'RECRUITING') {
			break;
		}
		sleep(15);
	}
	if (data(status, 'data.partyStatus') !== 'PAYING' || data(status, 'data.myPaid') === true) {
		return; // 파티 신청이 안 됐거나(자리 부족) 이미 냈다
	}
	s.api('GET', `/product/races/${raceId}`, { name: 'GET /product/races/{raceId}' });
	s.api('GET', `/member/crews/${crewId}`, { name: 'GET /member/crews/{crewId}' });
	const participationId = data(status, 'data.participationId');

	// 「10K · 40,000원 결제하기」: 내 몫 주문 (자리는 파티 신청 때 이미 확보)
	const ordered = s.api('POST', '/order/orders', {
		name: 'POST /order/orders', kind: 'write', expected: [200, 201],
		body: { participationId },
		headers: { 'Idempotency-Key': `rf-${participationId}` },
	});
	const orderId = data(ordered, 'data.id');
	if (!orderId) {
		broken(metricTags, 'member-order', ordered);
		return;
	}
	s.page(`/checkout?orderId=${orderId}`, 'PAGE /checkout');
	s.api('GET', `/order/orders/${orderId}`, { name: 'GET /order/orders/{orderId}' });
	s.api('GET', '/cash/wallets/balance', { name: 'GET /cash/wallets/balance' });
	s.api('GET', `/product/races/${raceId}`, { name: 'GET /product/races/{raceId}' });
	s.api('GET', `/order/participations/${participationId}`, { name: 'GET /order/participations/{id}' });
	s.api('GET', `/order/queue/admission?raceId=${raceId}&raceCourseId=${raceCourseId}`, { name: 'GET /order/queue/admission', kind: 'queue' });
	s.api('GET', `/file/files?refType=Race&refId=${raceId}`, { name: 'GET /file/files?refType=Race' });
	for (const c of race.courses) {
		s.api('GET', `/file/files?refType=RaceCourse&refId=${c.raceCourseId}`, { name: 'GET /file/files?refType=RaceCourse' });
	}
	sleep(THINK.pay);
	pay(s, { orderId, participationId, raceId, raceCourseId, metricTags });
}

// 테스트 계정 이메일인지 (안전장치: 다른 회원으로 로그인하지 않게)
export function isTestEmail(email) {
	return email.endsWith(`@${EMAIL_DOMAIN}`);
}
