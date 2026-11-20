-- 시나리오 B 공통 테스트 데이터 (로컬 전용). 다른 데이터는 지우지 않고 아래 번호대만 다시 만든다
-- 실행(backend/에서): ./loadtest/scenario-b/db.sh < loadtest/scenario-b/sql/01-seed.sql
-- 먼저 99-cleanup.sql로 이전 실행의 흔적을 지운다
--
-- 번호대
--   결제 회원  900001 ~ 901000 (지갑마다 1,000만 원 = 잔액 상한)
--   판매자     990001 ~ 990050 → 회원 900001 ~ 900050 (정산 지급의 sellerId는 회원 번호가 아니라 판매자 번호)
--   충전 확인  902001 (B-0 같은 충전 동시 승인, 잔액 0원)
--   환급 회원  909999(암호화 키 본뜨기용), 910001 ~ 910230 (seed-withdrawal.sh)
--   알림 회원  920001 ~ 939999 (seed-notice.sh)
--   충전 회원  940001 ~ 971000 (seed-charge.sh)
--   주문·정산 번호  900,000,000 + RUN x 1,000,000 + 순번 (k6 lib.js)

SET SESSION cte_max_recursion_depth = 100000;

-- 수수료(1)·보관(2) 회원과 지갑. 로컬 DB에는 이미 있어 그대로 두고, 측정 컨테이너의 빈 DB에서만 만든다
-- 빈 DB의 보관 지갑은 정산 지급이 첫 결제보다 먼저 와도 모자라지 않게 1억 원으로 시작한다 (원장 없이 넣어 돈 확인에는 섞이지 않음)
INSERT IGNORE INTO cash_member (id, nickname, create_date, modify_date) VALUES
	(1, 'system', NOW(), NOW()), (2, 'holding', NOW(), NOW());
INSERT IGNORE INTO cash_wallet (id, holder_id, balance, version, create_date, modify_date) VALUES
	(1, 1, 0, 0, NOW(), NOW()), (2, 2, 100000000, 0, NOW(), NOW());

INSERT INTO cash_member (id, nickname, create_date, modify_date)
WITH RECURSIVE seq (n) AS (SELECT 900001 UNION ALL SELECT n + 1 FROM seq WHERE n < 901000)
SELECT n, CONCAT('lt-b-', n), NOW(), NOW() FROM seq;

INSERT INTO cash_wallet (id, holder_id, balance, version, create_date, modify_date)
WITH RECURSIVE seq (n) AS (SELECT 900001 UNION ALL SELECT n + 1 FROM seq WHERE n < 901000)
SELECT n, n, 10000000, 0, NOW(), NOW() FROM seq;

INSERT INTO cash_member (id, nickname, create_date, modify_date) VALUES (902001, 'lt-b-charge', NOW(), NOW());
INSERT INTO cash_wallet (id, holder_id, balance, version, create_date, modify_date) VALUES (902001, 902001, 0, 0, NOW(), NOW());

INSERT INTO cash_seller (id, member_id, create_date, modify_date)
WITH RECURSIVE seq (n) AS (SELECT 990001 UNION ALL SELECT n + 1 FROM seq WHERE n < 990050)
SELECT n, n - 90000, NOW(), NOW() FROM seq;

SELECT COUNT(*) AS payer_wallets, SUM(balance) AS payer_balance
FROM cash_wallet WHERE id BETWEEN 900001 AND 901000;
