-- 시나리오 B 공통 테스트 데이터 (로컬 전용). 다른 데이터는 지우지 않고 아래 번호대만 다시 만든다
-- 실행(backend/에서): ./loadtest/scenario-b/db.sh < loadtest/scenario-b/sql/01-seed.sql
-- 먼저 99-cleanup.sql로 이전 실행의 흔적을 지운다
--
-- 번호대
--   결제 회원  900001 ~ 901000 (지갑마다 1,000만 원 = 잔액 상한)
--   판매자     990001 ~ 990050 → 회원 900001 ~ 900050 (정산 지급의 sellerId는 회원 번호가 아니라 판매자 번호)
--   환급 회원  909999(암호화 키 본뜨기용), 910001 ~ 910230 (02-seed-withdrawal.sh)
--   알림 회원  920001 ~ (03-seed-notice.sh)
--   주문·정산 번호  900,000,000 + RUN x 1,000,000 + 순번 (k6 lib.js)

SET SESSION cte_max_recursion_depth = 100000;

INSERT INTO cash_member (id, nickname, create_date, modify_date)
WITH RECURSIVE seq (n) AS (SELECT 900001 UNION ALL SELECT n + 1 FROM seq WHERE n < 901000)
SELECT n, CONCAT('lt-b-', n), NOW(), NOW() FROM seq;

INSERT INTO cash_wallet (id, holder_id, balance, version, create_date, modify_date)
WITH RECURSIVE seq (n) AS (SELECT 900001 UNION ALL SELECT n + 1 FROM seq WHERE n < 901000)
SELECT n, n, 10000000, 0, NOW(), NOW() FROM seq;

INSERT INTO cash_seller (id, member_id, create_date, modify_date)
WITH RECURSIVE seq (n) AS (SELECT 990001 UNION ALL SELECT n + 1 FROM seq WHERE n < 990050)
SELECT n, n - 90000, NOW(), NOW() FROM seq;

SELECT COUNT(*) AS payer_wallets, SUM(balance) AS payer_balance
FROM cash_wallet WHERE id BETWEEN 900001 AND 901000;
