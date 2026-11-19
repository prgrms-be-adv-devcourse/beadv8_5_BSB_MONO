#!/usr/bin/env bash
# B-5 충전 회원을 만든다: flow 940001 ~ 970000(30,000명), limit 970001 ~ 971000(1,000명)
# 실행(backend/에서): ./loadtest/scenario-b/seed-charge.sh   (B-5를 다시 돌리기 전에도 다시 한다)
# 처음 쓰는 사람처럼 가입한 지 24시간이 안 됐고(신규 회원 고액 충전 확인 조회가 한 번 더 나감) 잔액은 0원이다
set -euo pipefail
cd "$(dirname "$0")"

./db.sh <<'SQL'
SET SESSION cte_max_recursion_depth = 100000;
DELETE FROM cash_wallet_ledger WHERE wallet_id BETWEEN 940001 AND 971000;
DELETE FROM cash_wallet_charge WHERE wallet_id BETWEEN 940001 AND 971000;
DELETE FROM cash_wallet WHERE id BETWEEN 940001 AND 971000;
DELETE FROM cash_member WHERE id BETWEEN 940001 AND 971000;

INSERT INTO cash_member (id, nickname, create_date, modify_date)
WITH RECURSIVE seq (n) AS (SELECT 940001 UNION ALL SELECT n + 1 FROM seq WHERE n < 971000)
SELECT n, CONCAT('lt-b5-', n), NOW(), NOW() FROM seq;
INSERT INTO cash_wallet (id, holder_id, balance, version, create_date, modify_date)
WITH RECURSIVE seq (n) AS (SELECT 940001 UNION ALL SELECT n + 1 FROM seq WHERE n < 971000)
SELECT n, n, 0, 0, NOW(), NOW() FROM seq;

SELECT COUNT(*) AS 충전_회원, SUM(balance) AS 잔액_합 FROM cash_wallet WHERE id BETWEEN 940001 AND 971000;
SQL
