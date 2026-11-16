-- 돈 정합성 확인. 실행마다 끝나고 돌린다. 모든 "어긋난 건수"가 0이어야 통과
-- 실행(backend/에서): ./loadtest/scenario-b/verify.sh <RUN>
-- @run은 verify.sh가 앞에 넣는다. 주문·정산 번호는 900,000,000 + RUN x 1,000,000 + 순번

SET @lo = 900000000 + @run * 1000000;
SET @hi = @lo + 999999;

-- 1. 돈 보존: 이번 실행의 원장 합 + 은행으로 나간(나가는 중인) 정산 대금 = 0
--    결제(회원 -, 홀딩 +)·환불(반대)은 합이 0이고, 정산은 홀딩에서 빠진 대금만큼 은행으로 나간다
SELECT
	'1. 돈 보존 (0이어야 함)' AS 확인,
	COALESCE((SELECT SUM(l.amount) FROM cash_wallet_ledger l
		WHERE l.ref_type_code = 'Order' AND l.ref_id BETWEEN @lo AND @hi), 0)
	+ COALESCE((SELECT SUM(l.amount) FROM cash_wallet_ledger l
		JOIN cash_payout_transfer t ON l.ref_type_code = 'Payout' AND l.ref_id = t.id
		WHERE t.payout_id BETWEEN @lo AND @hi), 0)
	+ COALESCE((SELECT SUM(t.amount) FROM cash_payout_transfer t
		WHERE t.payout_id BETWEEN @lo AND @hi AND t.transfer_type = 'SETTLEMENT' AND t.status <> 'FAILED'), 0)
	AS 어긋난_금액;

-- 2. 결제 건별: 결제됨이면 회원 원장 합이 -결제액, 환불됨이면 0. 두 번 빠지거나 덜 돌려준 건을 센다
SELECT '2. 결제와 회원 원장이 다른 주문 (0이어야 함)' AS 확인, COUNT(*) AS 어긋난_건수
FROM cash_payment p
LEFT JOIN (
	SELECT ref_id, SUM(amount) AS total FROM cash_wallet_ledger
	WHERE ref_type_code = 'Order' AND ref_id BETWEEN @lo AND @hi AND wallet_id <> 2
	GROUP BY ref_id
) c ON c.ref_id = p.order_id
WHERE p.order_id BETWEEN @lo AND @hi
	AND COALESCE(c.total, 0) <> CASE p.status WHEN 'COMPLETED' THEN -p.amount WHEN 'REFUNDED' THEN 0 ELSE 0 END;

-- 3. 같은 주문에 같은 방향 원장이 두 줄 이상 (중복 결제·중복 환불, 0이어야 함)
SELECT '3. 중복 원장 (0이어야 함)' AS 확인, COUNT(*) AS 어긋난_건수
FROM (
	SELECT wallet_id, ref_id, SIGN(amount) AS dir, COUNT(*) AS cnt FROM cash_wallet_ledger
	WHERE ref_type_code = 'Order' AND ref_id BETWEEN @lo AND @hi
	GROUP BY wallet_id, ref_id, SIGN(amount) HAVING cnt > 1
) d;

-- 4. 정산 건별: 홀딩에서 빠진 금액 = 송금 기록 금액 (실패는 되돌려 0)
SELECT '4. 정산과 홀딩 원장이 다른 송금 (0이어야 함)' AS 확인, COUNT(*) AS 어긋난_건수
FROM cash_payout_transfer t
LEFT JOIN (
	SELECT ref_id, SUM(amount) AS total FROM cash_wallet_ledger
	WHERE ref_type_code = 'Payout' AND wallet_id = 2 GROUP BY ref_id
) h ON h.ref_id = t.id
WHERE t.payout_id BETWEEN @lo AND @hi
	AND COALESCE(h.total, 0) <> CASE t.status WHEN 'FAILED' THEN 0 ELSE -t.amount END;

-- 5. 지갑 잔액 = 마지막 원장의 거래 후 잔액 (시나리오 B 회원·홀딩·시스템)
SELECT '5. 잔액과 마지막 원장이 다른 지갑 (0이어야 함)' AS 확인, COUNT(*) AS 어긋난_건수
FROM cash_wallet w
JOIN cash_wallet_ledger l ON l.id = (SELECT MAX(id) FROM cash_wallet_ledger WHERE wallet_id = w.id)
WHERE (w.id BETWEEN 900001 AND 999999 OR w.id IN (1, 2))
	AND w.balance <> l.balance_after;

-- 참고: 이번 실행의 건수
SELECT '참고: 결제 상태별' AS 확인, status, COUNT(*) AS 건수
FROM cash_payment WHERE order_id BETWEEN @lo AND @hi GROUP BY status;
SELECT '참고: 정산 송금 상태별' AS 확인, transfer_type, status, COUNT(*) AS 건수
FROM cash_payout_transfer WHERE payout_id BETWEEN @lo AND @hi GROUP BY transfer_type, status;
