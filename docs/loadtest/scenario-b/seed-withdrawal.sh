#!/usr/bin/env bash
# B-3 환급 회원을 만든다 (cash를 띄운 뒤, 실행마다 다시). 환급하면 잔액이 0이 되므로 B-3을 다시 돌리기 전에 또 실행한다
# 실행(backend/에서): ./loadtest/scenario-b/seed-withdrawal.sh
#
# 카드 충전 기록의 PG 결제 키는 앱이 암호화해 저장한다. SQL로 평문을 넣으면 읽을 때 복호화에 실패하므로,
# 909999번 회원으로 충전 API를 한 번 불러 앱이 만든 암호문을 얻고 그 값을 다른 충전 기록에 그대로 복사한다
# (가짜 PG는 결제 키 값을 보지 않으므로 같은 키여도 된다)
#
#   카드 1장 910001 ~ 910030 / 3장 910101 ~ 910130 / 5장 910201 ~ 910230, 장마다 1만 원
#   예열(버림) 카드 1장 910301 ~ 910310: 막 띄운 뒤 첫 환급이 느린 것이 1장 묶음에 몰리지 않게 먼저 환급한다
set -euo pipefail
cd "$(dirname "$0")"
CASH_URL="${CASH_URL:-http://localhost:8081}"

./db.sh <<'SQL'
SET SESSION cte_max_recursion_depth = 100000;
CREATE TEMPORARY TABLE lt_w (member_id BIGINT PRIMARY KEY, cards INT);
INSERT INTO lt_w
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 30)
SELECT 910000 + n, 1 FROM seq UNION ALL
SELECT 910100 + n, 3 FROM seq UNION ALL
SELECT 910200 + n, 5 FROM seq UNION ALL
SELECT 910300 + n, 1 FROM seq WHERE n <= 10;
INSERT INTO lt_w VALUES (909999, 0);

-- 이전 B-3 실행의 흔적을 지운다
DELETE i FROM cash_wallet_withdrawal_item i
JOIN cash_wallet_withdrawal w ON w.id = i.withdrawal_id WHERE w.wallet_id IN (SELECT member_id FROM lt_w);
DELETE FROM cash_wallet_withdrawal WHERE wallet_id IN (SELECT member_id FROM lt_w);
DELETE FROM cash_wallet_ledger WHERE wallet_id IN (SELECT member_id FROM lt_w);
DELETE FROM cash_wallet_charge WHERE wallet_id IN (SELECT member_id FROM lt_w);
DELETE FROM cash_wallet WHERE id IN (SELECT member_id FROM lt_w);
DELETE FROM cash_member WHERE id IN (SELECT member_id FROM lt_w);

INSERT INTO cash_member (id, nickname, create_date, modify_date)
SELECT member_id, CONCAT('lt-b3-', member_id), NOW(), NOW() FROM lt_w;
INSERT INTO cash_wallet (id, holder_id, balance, version, create_date, modify_date)
SELECT member_id, member_id, cards * 10000, 0, NOW(), NOW() FROM lt_w;
SQL

echo "암호문 본뜨기용 충전 (909999번 회원)"
curl -sf -X POST "${CASH_URL}/api/v1/cash/charges/confirm" \
	-H 'Content-Type: application/json' -H 'X-Member-Id: 909999' \
	-d "{\"paymentKey\":\"lt-b3-template\",\"orderId\":\"lt-b3-template-$(date +%s)\",\"amount\":10000}" >/dev/null

./db.sh <<'SQL'
SET SESSION cte_max_recursion_depth = 100000;
SELECT pg_payment_key, pg_provider INTO @key, @provider
FROM cash_wallet_charge WHERE wallet_id = 909999 ORDER BY id DESC LIMIT 1;

-- 하루 전에 승인된 카드 충전: 결제취소 기한(1년) 안이라 환급하면 모두 카드 결제취소로 간다
INSERT INTO cash_wallet_charge (amount, canceled_amount, charge_date, approved_at, idempotency_key, pg_payment_key,
	pg_provider, review_flagged, status, wallet_id, create_date, modify_date)
WITH RECURSIVE card (k) AS (SELECT 1 UNION ALL SELECT k + 1 FROM card WHERE k < 5)
SELECT 10000, 0, NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY, CONCAT('lt-b3-', w.id, '-', card.k), @key,
	@provider, 0, 'COMPLETED', w.id, NOW(), NOW()
FROM cash_wallet w JOIN card ON card.k <= w.balance / 10000
WHERE w.id BETWEEN 910001 AND 910310;

SELECT w.balance / 10000 AS 카드_장수, COUNT(DISTINCT w.id) AS 회원_수, COUNT(c.id) AS 충전_기록
FROM cash_wallet w JOIN cash_wallet_charge c ON c.wallet_id = w.id
WHERE w.id BETWEEN 910001 AND 910310 GROUP BY w.balance;
SQL
