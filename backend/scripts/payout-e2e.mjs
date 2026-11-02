// 정산 E2E: 주문(8084) → 판매 내역 → 정산 생성 배치(8082) → 판매자 조회 → 환불 막기 → 보류 · 해제 → 지급 배치
// 시나리오와 기대 결과는 같은 폴더의 payout-e2e.md에 있다 (PRO-62)
//
// 사용법: node scripts/payout-e2e.mjs [--skip-pay]
//   dev 프로파일로 order-service(8084), payout-service(8082)와 bukang-db 컨테이너가 떠 있어야 한다
//   --skip-pay: 지급 배치를 돌리지 않는다. 지급 배치는 이 스크립트 데이터만이 아니라 dev DB의
//               지급 예정일이 지난 CALCULATED 정산 내역을 모두 지급 완료로 바꾼다
import { execFileSync } from 'node:child_process';

const ORDER = process.env.ORDER_URL ?? 'http://localhost:8084';
const PAYOUT = process.env.PAYOUT_URL ?? 'http://localhost:8082';
const DB_CONTAINER = process.env.DB_CONTAINER ?? 'bukang-db';
const SELLER = 1;   // 주문 서비스 FakeRaceCourseAdapter의 판매자 ID
const ADMIN = 3;    // 보류 · 해제 요청자 (헤더 값만 기록된다)
const FEE_RATE = 0.055;
const skipPay = process.argv.includes('--skip-pay');

let pass = 0;
let fail = 0;
const check = (name, ok, detail = '') => {
	ok ? pass++ : fail++;
	console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  — ' + detail : ''}`);
};
const note = (name, detail) => console.log(`NOTE  ${name}  — ${detail}`);

async function call(method, url, { member, body, headers = {} } = {}) {
	const h = { 'Content-Type': 'application/json', ...headers };
	if (member) {
		h['X-Member-Id'] = String(member);
	}
	const res = await fetch(url, { method, headers: h, body: body ? JSON.stringify(body) : undefined });
	const text = await res.text();
	let json;
	try {
		json = JSON.parse(text);
	} catch {
		json = text;
	}
	return { http: res.status, json };
}

const sql = query => execFileSync('docker',
	['exec', DB_CONTAINER, 'mysql', '-ubukang2', '-pbukang2', 'bukang-db', '-e', query],
	{ encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });

// 날짜는 'YYYY-MM-DD' 문자열로 다룬다. 서버와 같이 한국 날짜를 오늘로 본다
const today = new Date(Date.now() + 9 * 3600e3).toISOString().slice(0, 10);
const addDays = (date, n) => {
	const d = new Date(date + 'T00:00:00Z');
	d.setUTCDate(d.getUTCDate() + n);
	return d.toISOString().slice(0, 10);
};
const monthOf = date => date.slice(0, 7);
const nextMonthOf = month => monthOf(addDays(month + '-01', 31));
// 서버의 지급일 규칙과 같다: 그 달 20일, 주말이면 다음 월요일 (Payout.payDayOf, 세미는 주말만 휴일)
const payDayOf = month => {
	let day = month + '-20';
	while ([0, 6].includes(new Date(day + 'T00:00:00Z').getUTCDay())) {
		day = addDays(day, 1);
	}
	return day;
};
const fee = price => Math.floor(price * FEE_RATE);

// 참가 신청 → 주문 → (결제) → (취소). 구매자는 매번 다른 회원 ID를 쓴다
let buyer = 1000 + Math.floor(Math.random() * 100000);
async function buy(courseId, { pay = true, cancel = false } = {}) {
	const member = buyer++;
	const apply = await call('POST', `${ORDER}/api/v1/order/participations`, { member, body: {
		raceCourseId: courseId,
		thirdPartyConsent: true,
		participant: { name: `E2E${member}`, birth: '1995-01-01', gender: 'FEMALE',
			phone: '010-2222-' + String(member % 10000).padStart(4, '0'),
			emergencyContactName: '보호자', emergencyContactPhone: '010-9999-0000', tshirtSize: 'S' } } });
	if (apply.http !== 201) {
		throw new Error('참가 신청 실패 ' + JSON.stringify(apply.json));
	}
	const order = await call('POST', `${ORDER}/api/v1/order/orders`, { member,
		headers: { 'Idempotency-Key': crypto.randomUUID() }, body: { participationId: apply.json.data.id } });
	if (order.http !== 201) {
		throw new Error('주문 실패 ' + JSON.stringify(order.json));
	}
	const orderId = order.json.data.id;
	const itemId = order.json.data.items[0].id;
	if (pay) {
		const p = await call('POST', `${ORDER}/api/v1/order/orders/${orderId}/payment`, { member });
		if (p.http !== 200) {
			throw new Error('결제 실패 ' + JSON.stringify(p.json));
		}
	}
	if (cancel) {
		const c = await call('POST', `${ORDER}/api/v1/order/orders/${orderId}/items/${itemId}/cancel`, { member });
		if (c.http !== 200) {
			throw new Error('취소 실패 ' + JSON.stringify(c.json));
		}
	}
	return { member, orderId, itemId };
}

// ── 1. 데이터 만들기 ─────────────────────────────────────────────
// 배치는 오늘 실행하면 어제부터 7일치(오늘-7 ~ 오늘-1) 대회를 본다
const raceDateA = addDays(today, -1);   // 어제: 오늘이 정산 대상일
const raceDateC = addDays(today, -7);   // 되돌아보기 첫날 (포함)
const raceDateB = addDays(today, -8);   // 되돌아보기 밖 (제외)
const base = 800000 + Math.floor(Math.random() * 90000) * 10;
const A = base + 1;
const C = base + 2;
const B = base + 3;

const a1 = await buy(A);
const a2 = await buy(A);
const aUnpaid = await buy(A, { pay: false });
const aCanceled = await buy(A, { cancel: true });
const c1 = await buy(C);
const b1 = await buy(B);
console.log(`오늘 ${today}`);
console.log(`대회 A=${A}(${raceDateA}) C=${C}(${raceDateC}) B=${B}(${raceDateB})`);
console.log(`주문 상품 A: 결제 ${a1.itemId}, ${a2.itemId} / 결제 전 ${aUnpaid.itemId} / 결제 후 취소 ${aCanceled.itemId}`
	+ `, C: ${c1.itemId}, B: ${b1.itemId}`);

// 가짜 종목 어댑터는 대회일을 늘 '지금 + 60일'로 주므로, 결제 · 취소를 마친 뒤 대회일만 과거로 옮겨
// "결제하고 시간이 지나 대회가 열린" 상태를 만든다 (이 단계만 API가 아니라 DB를 직접 고친다)
sql(`update order_order_item set race_date = case race_id
	when ${A} then '${raceDateA} 08:00:00' when ${C} then '${raceDateC} 08:00:00' when ${B} then '${raceDateB} 08:00:00' end
	where race_id in (${A}, ${C}, ${B})`);

// ── 2. 판매 내역 (배치처럼 대회 ID로 묻는다) ─────────────────────
const soldOf = async raceId => (await call('GET', `${ORDER}/internal/v1/order/sold-entries?raceId=${raceId}`)).json.data;
const soldA = await soldOf(A);
const soldC = await soldOf(C);
const sold = [...soldA, ...soldC];
const soldIds = sold.map(e => e.orderItemId);
check('A: 결제한 2장이 나옴', soldIds.includes(a1.itemId) && soldIds.includes(a2.itemId));
check('A: 결제 전(ORDERED) 제외', !soldIds.includes(aUnpaid.itemId));
check('A: 결제 후 취소(CANCELED) 제외', !soldIds.includes(aCanceled.itemId));
check('C: 그 대회 참가권만 나옴', soldC.length === 1 && soldC[0].orderItemId === c1.itemId);
check('A: 다른 대회(B) 참가권 제외', !soldIds.includes(b1.itemId));
const priceOf = itemId => sold.find(e => e.orderItemId === itemId).price;

const missingRaceId = await call('GET', `${ORDER}/internal/v1/order/sold-entries`);
check('판매 내역: 대회 ID가 없으면 400', missingRaceId.http === 400, `HTTP ${missingRaceId.http}`);

// ── 3. 정산 생성 배치 (오늘 실행) ─────────────────────────────────
const first = (await call('POST', `${PAYOUT}/api/v1/payout/dev/batches/create?today=${today}`)).json.data;
check('조회 범위가 오늘-7 ~ 오늘-1', first.fromRaceDate === raceDateC && first.toRaceDate === raceDateA,
	`${first.fromRaceDate} ~ ${first.toRaceDate}`);
check('A, C 두 묶음 생성, 실패 0', first.created === 2 && first.failed === 0, JSON.stringify(first));
note('건너뜀', `${first.skipped}건 (dev DB에 이미 정산 내역이 있는 묶음)`);
const second = (await call('POST', `${PAYOUT}/api/v1/payout/dev/batches/create?today=${today}`)).json.data;
check('같은 날 다시 실행하면 생성 0', second.created === 0 && second.failed === 0, JSON.stringify(second));

// ── 4. 판매자 조회 ───────────────────────────────────────────────
const targetA = today;
const targetC = addDays(raceDateC, 1);
const list = async () => (await call('GET', `${PAYOUT}/api/v1/payout/seller/payouts`
	+ `?fromMonth=${monthOf(targetC)}&toMonth=${monthOf(targetA)}`, { member: SELLER })).json.data;
const payouts = await list();
const pA = payouts.find(p => p.raceId === A);
const pC = payouts.find(p => p.raceId === C);
console.log('  정산 내역 A', JSON.stringify(pA));
console.log('  정산 내역 C', JSON.stringify(pC));
check('B는 정산 내역 없음', !payouts.some(p => p.raceId === B));

const expectPayout = (name, p, target, itemIds) => {
	const sales = itemIds.reduce((sum, id) => sum + priceOf(id), 0);
	const fees = itemIds.reduce((sum, id) => sum + fee(priceOf(id)), 0);
	check(`${name}: 정산 월 = 정산 대상일(${target})의 달`, p?.payoutMonth === monthOf(target), p?.payoutMonth);
	// 정산 월은 1차면 환불 마감일, 최종이면 대회일의 달이다. 어느 단계든 지급 예정일은 정산 월의 다음 달 20일이다
	// 정산 월이 없으면(정산 내역 없음 · 필드 이름 바뀜) 날짜 계산이 예외를 던져 뒤 검증이 모두 건너뛰어지므로 계산하지 않고 FAIL로 남긴다
	const expectedPayDate = p?.payoutMonth ? payDayOf(nextMonthOf(p.payoutMonth)) : undefined;
	check(`${name}: 지급 예정일 = 정산 월의 다음 달 20일(휴일이면 다음 영업일)`,
		expectedPayDate !== undefined && p?.scheduledPayDate === expectedPayDate, p?.scheduledPayDate);
	check(`${name}: 매출 ${sales} / 수수료 ${fees} / 지급액 ${sales - fees}`,
		p?.salesAmount === sales && p?.feeAmount === fees && p?.payoutAmount === sales - fees && p?.refundAmount === 0);
	check(`${name}: 상태 CALCULATED`, p?.status === 'CALCULATED', p?.status);
};
expectPayout('A', pA, targetA, [a1.itemId, a2.itemId]);
expectPayout('C', pC, targetC, [c1.itemId]);

const items = (await call('GET', `${PAYOUT}/api/v1/payout/seller/payout-items?from=${targetC}&to=${targetA}`,
	{ member: SELLER })).json.data.content;
const itemOf = itemId => items.find(r => r.orderItemId === itemId);
check('정산 항목: 참가권 1장 = 1행 (A 2행, C 1행)', itemOf(a1.itemId) && itemOf(a2.itemId) && itemOf(c1.itemId));
check('정산 항목의 정산 대상일 (A 오늘, C 대회 다음 날)', itemOf(a1.itemId)?.targetDate === targetA
	&& itemOf(c1.itemId)?.targetDate === targetC);
check('결제 전 · 취소 참가권은 정산 항목 없음', !itemOf(aUnpaid.itemId) && !itemOf(aCanceled.itemId));
note('요약', JSON.stringify((await call('GET', `${PAYOUT}/api/v1/payout/seller/summary`, { member: SELLER })).json.data));

// ── 5. 정산 대상일부터 환불 막기 ──────────────────────────────────
const eligible = (await call('GET', `${PAYOUT}/internal/v1/payout/refund-eligibility?raceDate=${today}`)).json.data;
check('환불 가능 여부: 오늘 대회는 true', eligible.refundable === true, JSON.stringify(eligible));
const blocked = (await call('GET', `${PAYOUT}/internal/v1/payout/refund-eligibility?raceDate=${raceDateA}`)).json.data;
check('환불 가능 여부: 어제 대회(오늘이 정산 대상일)는 false', blocked.refundable === false, JSON.stringify(blocked));
const lateCancel = await call('POST', `${ORDER}/api/v1/order/orders/${a1.orderId}/items/${a1.itemId}/cancel`,
	{ member: a1.member });
check('정산된 참가권 취소는 409', lateCancel.http === 409, `HTTP ${lateCancel.http} ${lateCancel.json?.message ?? ''}`);

// ── 6. 지급보류 · 해제 (C) ────────────────────────────────────────
const hold = await call('POST', `${PAYOUT}/api/v1/payout/payouts/${pC.id}/holds`, { member: ADMIN,
	body: { reasonType: 'RACE_CANCELED', reason: 'E2E 보류 확인' } });
check('C 지급보류', hold.http === 201, `HTTP ${hold.http} holdId=${hold.json?.data?.id}`);
const holdAgain = await call('POST', `${PAYOUT}/api/v1/payout/payouts/${pC.id}/holds`, { member: ADMIN,
	body: { reasonType: 'RACE_CANCELED', reason: '중복' } });
check('보류 중 다시 보류하면 409', holdAgain.http === 409, `HTTP ${holdAgain.http}`);
check('보류 뒤 상태 HELD', (await list()).find(p => p.id === pC.id)?.status === 'HELD');
const release = await call('POST', `${PAYOUT}/api/v1/payout/holds/${hold.json.data.id}/release`, { member: ADMIN });
check('C 해제', release.http === 200, `HTTP ${release.http}`);
const releasedC = (await list()).find(p => p.id === pC.id);
check('해제 뒤 상태 CALCULATED', releasedC?.status === 'CALCULATED', releasedC?.status);
// 해제 뒤 지급 예정일 = max(원래 지급 예정일, 해제일 + 1일). 원래보다 앞당기지 않는다
const payDateAfterRelease = addDays(today, 1);
const expectReleasedPayDate = payDateAfterRelease > pC.scheduledPayDate ? payDateAfterRelease : pC.scheduledPayDate;
check('해제 뒤 C 지급 예정일 = 원래 지급 예정일과 해제일 다음 날 중 늦은 날',
	releasedC?.scheduledPayDate === expectReleasedPayDate,
	`${releasedC?.scheduledPayDate} (원래 ${pC.scheduledPayDate}, 해제일 ${today})`);

// ── 7. 지급 배치 ─────────────────────────────────────────────────
if (skipPay) {
	console.log('SKIP  지급 배치 (--skip-pay)');
} else {
	const payDays = [...new Set([releasedC.scheduledPayDate, pA.scheduledPayDate])]
		.flatMap(day => [addDays(day, -1), day])
		.sort();
	for (const day of payDays) {
		const result = (await call('POST', `${PAYOUT}/api/v1/payout/dev/batches/pay?today=${day}`)).json.data;
		const now = await list();
		const statusA = now.find(p => p.id === pA.id).status;
		const statusC = now.find(p => p.id === pC.id).status;
		const expectA = day >= pA.scheduledPayDate ? 'PAID' : 'CALCULATED';
		const expectC = day >= releasedC.scheduledPayDate ? 'PAID' : 'CALCULATED';
		check(`지급 배치 ${day}: A ${expectA}, C ${expectC}`, statusA === expectA && statusC === expectC,
			`지급 ${result.paid}건, A ${statusA}, C ${statusC}`);
	}
}

console.log(`\n${pass} passed, ${fail} failed`);
process.exitCode = fail === 0 ? 0 : 1;
