-- 충전 확인 (C6). B-0(같은 충전 동시 승인)과 B-5 뒤에 돌린다. 어긋난 건수가 모두 0이어야 통과
-- 같은 충전 번호로 충전 기록이 두 개 생기는 일은 유니크 키가 막아서 따로 세지 않는다
-- 대상: 충전 확인 회원 902001, 충전 회원 940001 ~ 971000. 이 회원들은 충전만 하고 결제·환급은 하지 않는다
-- 실행(backend/에서): ./loadtest/scenario-b/db.sh < loadtest/scenario-b/sql/12-verify-charge.sql

-- 6-1. 충전 한 건에 충전 원장이 두 줄 이상 (같은 충전이 두 번 반영, 0이어야 함)
SELECT '6-1. 충전 원장이 두 줄 이상인 충전 (0이어야 함)' AS 확인, COUNT(*) AS 어긋난_건수
FROM (
	SELECT l.ref_id FROM cash_wallet_ledger l
	JOIN cash_wallet_charge c ON c.id = l.ref_id
	WHERE l.ref_type_code = 'WalletCharge' AND (c.wallet_id = 902001 OR c.wallet_id BETWEEN 940001 AND 971000)
	GROUP BY l.ref_id HAVING COUNT(*) > 1
) d;

-- 6-2. 지갑 잔액 = 완료된 충전 금액 합 (0이어야 함)
SELECT '6-2. 잔액과 완료 충전 합이 다른 지갑 (0이어야 함)' AS 확인, COUNT(*) AS 어긋난_건수
FROM cash_wallet w
LEFT JOIN (
	SELECT wallet_id, SUM(amount) AS total FROM cash_wallet_charge WHERE status = 'COMPLETED' GROUP BY wallet_id
) c ON c.wallet_id = w.id
WHERE (w.id = 902001 OR w.id BETWEEN 940001 AND 971000) AND w.balance <> COALESCE(c.total, 0);

-- 참고: 충전 상태별 건수 (완료가 아닌 것이 있으면 응답 실패와 같이 본다)
SELECT '참고: 충전 상태별' AS 확인, status, COUNT(*) AS 건수
FROM cash_wallet_charge WHERE wallet_id = 902001 OR wallet_id BETWEEN 940001 AND 971000 GROUP BY status;
