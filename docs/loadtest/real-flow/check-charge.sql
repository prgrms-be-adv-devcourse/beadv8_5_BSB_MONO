-- 2부(charge-ramp.js) 정합성 확인. 운영 보조 서버 MySQL에서 돌린다. 「(0이어야 함)」 줄이 모두 0이면 통과
-- @prefix는 charge-ramp.js가 준비 끝에 찍은 「충전 주문번호 앞머리」 (예: charge-cr-mihg2k1a-)
-- 테스트 계정 잔액은 charge-prod.sql이 원장 없이 바꿔 둬서 「잔액 = 원장 합」으로는 못 본다
-- 그래서 이번 실행에서 생긴 원장부터 앞뒤 원장의 잔액이 이어지는지, 마지막 원장 잔액이 지금 잔액과 같은지 본다
SET @prefix = 'charge-cr-여기에-앞머리-';

-- 참고: 이번 실행 충전의 상태별 건수. 완료가 아닌 것은 k6의 charge_failed와 같이 본다
SELECT '참고: 충전 상태별' AS `확인`, c.status, COUNT(*) AS `건수`, SUM(c.amount) AS `금액`
FROM crewrun_cash.cash_wallet_charge c
WHERE c.idempotency_key LIKE CONCAT(@prefix, '%')
GROUP BY c.status;

-- 1. 충전 한 건에 충전 원장이 두 줄 이상 (같은 충전이 두 번 반영)
SELECT '1. 원장이 두 줄 이상인 충전 (0이어야 함)' AS `확인`, COUNT(*) AS `어긋난 건수`
FROM (
	SELECT l.ref_id
	FROM crewrun_cash.cash_wallet_ledger l
		JOIN crewrun_cash.cash_wallet_charge c ON c.id = l.ref_id
	WHERE l.ref_type_code = 'WalletCharge' AND c.idempotency_key LIKE CONCAT(@prefix, '%')
	GROUP BY l.ref_id
	HAVING COUNT(*) > 1
) d;

-- 2. 완료됐는데 원장이 없는 충전 / 완료가 아닌데 원장이 있는 충전 (잔액에 반영된 것과 충전 상태가 다름)
SELECT '2. 상태와 원장이 다른 충전 (0이어야 함)' AS `확인`, COUNT(*) AS `어긋난 건수`
FROM crewrun_cash.cash_wallet_charge c
	LEFT JOIN crewrun_cash.cash_wallet_ledger l ON l.ref_type_code = 'WalletCharge' AND l.ref_id = c.id
WHERE c.idempotency_key LIKE CONCAT(@prefix, '%')
	AND ((c.status = 'COMPLETED' AND l.id IS NULL) OR (c.status <> 'COMPLETED' AND l.id IS NOT NULL));

-- 3·4는 이번 실행 충전이 닿은 지갑의, 이번 실행 첫 원장부터의 원장을 본다
DROP TEMPORARY TABLE IF EXISTS cr_ledger;
CREATE TEMPORARY TABLE cr_ledger AS
SELECT l.id, l.wallet_id, l.amount, l.balance_after,
	LAG(l.balance_after) OVER (PARTITION BY l.wallet_id ORDER BY l.id) AS prev_after,
	ROW_NUMBER() OVER (PARTITION BY l.wallet_id ORDER BY l.id DESC) AS from_last
FROM crewrun_cash.cash_wallet_ledger l
	JOIN (
		SELECT c.wallet_id, MIN(l2.id) AS first_id
		FROM crewrun_cash.cash_wallet_charge c
			JOIN crewrun_cash.cash_wallet_ledger l2 ON l2.ref_type_code = 'WalletCharge' AND l2.ref_id = c.id
		WHERE c.idempotency_key LIKE CONCAT(@prefix, '%')
		GROUP BY c.wallet_id
	) w ON w.wallet_id = l.wallet_id AND l.id >= w.first_id;

-- 3. 앞 원장 잔액 + 이번 변동 ≠ 이번 원장 잔액 (잔액 갱신을 잃어버림)
SELECT '3. 잔액이 이어지지 않는 원장 (0이어야 함)' AS `확인`, COUNT(*) AS `어긋난 건수`
FROM cr_ledger
WHERE prev_after IS NOT NULL AND prev_after + amount <> balance_after;

-- 4. 지갑의 마지막 원장 잔액 ≠ 지금 잔액
SELECT '4. 마지막 원장과 잔액이 다른 지갑 (0이어야 함)' AS `확인`, COUNT(*) AS `어긋난 건수`
FROM cr_ledger x
	JOIN crewrun_cash.cash_wallet w ON w.id = x.wallet_id
WHERE x.from_last = 1 AND x.balance_after <> w.balance;

DROP TEMPORARY TABLE cr_ledger;
