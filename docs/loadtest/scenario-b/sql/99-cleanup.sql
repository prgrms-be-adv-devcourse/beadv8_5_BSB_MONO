-- 시나리오 B가 만든 데이터만 지운다 (로컬 전용). 01-seed.sql의 번호대와 주문·정산 번호 900,000,000 이상
-- 실행(backend/에서): ./loadtest/scenario-b/db.sh < loadtest/scenario-b/sql/99-cleanup.sql
--
-- 홀딩(2번)·시스템(1번) 지갑은 다른 데이터와 같이 쓰므로 지우지 않는다.
-- 시나리오 B 원장만큼 잔액을 되돌린 뒤 그 원장을 지운다 → 잔액과 남은 원장의 합이 테스트 전과 같아진다

SET @lt_member_lo = 900001;
SET @lt_member_hi = 999999;
SET @lt_ref_lo = 900000000;

-- 정산 원장은 송금 기록 번호를 ref_id로 쓴다
CREATE TEMPORARY TABLE lt_transfer AS
SELECT id FROM cash_payout_transfer WHERE payout_id >= @lt_ref_lo;

CREATE TEMPORARY TABLE lt_ledger AS
SELECT l.id, l.wallet_id, l.amount
FROM cash_wallet_ledger l
WHERE l.wallet_id BETWEEN @lt_member_lo AND @lt_member_hi
	OR (l.ref_type_code = 'Order' AND l.ref_id >= @lt_ref_lo)
	OR (l.ref_type_code = 'Payout' AND l.ref_id IN (SELECT id FROM lt_transfer));

UPDATE cash_wallet w
JOIN (SELECT wallet_id, SUM(amount) AS total FROM lt_ledger WHERE wallet_id IN (1, 2) GROUP BY wallet_id) t
	ON t.wallet_id = w.id
SET w.balance = w.balance - t.total;

DELETE FROM cash_wallet_ledger WHERE id IN (SELECT id FROM lt_ledger);
DELETE FROM cash_payment WHERE order_id >= @lt_ref_lo OR wallet_id BETWEEN @lt_member_lo AND @lt_member_hi;
-- 가짜 은행 송금 번호는 "payout-<송금 기록 번호>-<시도>"
DELETE FROM cash_fakebank_transfer WHERE seller_id BETWEEN 990001 AND 990050;
DELETE FROM cash_payout_transfer WHERE payout_id >= @lt_ref_lo;
DELETE i FROM cash_wallet_withdrawal_item i
JOIN cash_wallet_withdrawal w ON w.id = i.withdrawal_id
WHERE w.wallet_id BETWEEN @lt_member_lo AND @lt_member_hi;
DELETE FROM cash_wallet_withdrawal WHERE wallet_id BETWEEN @lt_member_lo AND @lt_member_hi;
DELETE FROM cash_wallet_charge WHERE wallet_id BETWEEN @lt_member_lo AND @lt_member_hi;
DELETE FROM cash_seller WHERE id BETWEEN 990001 AND 990050;
DELETE FROM cash_wallet WHERE id BETWEEN @lt_member_lo AND @lt_member_hi;
DELETE FROM cash_member WHERE id BETWEEN @lt_member_lo AND @lt_member_hi;

SELECT id, balance FROM cash_wallet WHERE id IN (1, 2);
