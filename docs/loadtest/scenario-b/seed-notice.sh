#!/usr/bin/env bash
# B-4 기한 알림 대상 회원 N명을 만든다. 실행(backend/에서): ./loadtest/scenario-b/seed-notice.sh 1000
# 회원마다 결제취소 마지막 날이 정확히 3일 뒤인 카드 충전 1건(1만 원)과 잔액 1만 원을 넣는다
# 마지막 날 = 승인일 + 1년 − 1일이므로 승인일 = 오늘 + 4일 − 1년. 실행한 날에만 맞으니 배치도 같은 날 돌린다
# PG 결제 키는 비워 둔다 (알림 배치는 결제 키를 쓰지 않고, 빈 값은 복호화하지 않는다)
set -euo pipefail
cd "$(dirname "$0")"
N="${1:?회원 수를 넣어 주세요. 예: ./seed-notice.sh 1000}"
# 920001 ~ 939999만 쓴다. 그 위는 충전 회원(seed-charge.sh) 번호대
if (( N < 1 || N > 19999 )); then echo "1 ~ 19999명만 만들 수 있어요"; exit 1; fi

./db.sh <<SQL
SET SESSION cte_max_recursion_depth = 100000;
DELETE FROM cash_wallet_charge WHERE wallet_id BETWEEN 920001 AND 939999;
DELETE FROM cash_wallet WHERE id BETWEEN 920001 AND 939999;
DELETE FROM cash_member WHERE id BETWEEN 920001 AND 939999;

INSERT INTO cash_member (id, nickname, create_date, modify_date)
WITH RECURSIVE seq (n) AS (SELECT 920001 UNION ALL SELECT n + 1 FROM seq WHERE n < 920000 + ${N})
SELECT n, CONCAT('lt-b4-', n), NOW(), NOW() FROM seq;
INSERT INTO cash_wallet (id, holder_id, balance, version, create_date, modify_date)
WITH RECURSIVE seq (n) AS (SELECT 920001 UNION ALL SELECT n + 1 FROM seq WHERE n < 920000 + ${N})
SELECT n, n, 10000, 0, NOW(), NOW() FROM seq;
INSERT INTO cash_wallet_charge (amount, canceled_amount, charge_date, approved_at, idempotency_key, pg_payment_key,
	pg_provider, review_flagged, status, wallet_id, create_date, modify_date)
WITH RECURSIVE seq (n) AS (SELECT 920001 UNION ALL SELECT n + 1 FROM seq WHERE n < 920000 + ${N})
SELECT 10000, 0, approved, approved, CONCAT('lt-b4-', n), NULL, 'TOSS', 0, 'COMPLETED', n, NOW(), NOW()
FROM seq, (SELECT TIMESTAMP(CURDATE() + INTERVAL 4 DAY - INTERVAL 1 YEAR, '10:00:00') AS approved) a;

-- 시나리오 B 밖의 회원 중 같은 날 대상인 사람도 배치가 함께 본다. 결과 문서에 같이 적는다
SELECT
	SUM(wallet_id BETWEEN 920001 AND 939999) AS 시나리오_대상,
	SUM(wallet_id NOT BETWEEN 920001 AND 939999) AS 그_밖의_대상
FROM cash_wallet_charge
WHERE status = 'COMPLETED'
	AND approved_at >= CURDATE() + INTERVAL 1 DAY - INTERVAL 1 YEAR
	AND approved_at < CURDATE() + INTERVAL 7 DAY - INTERVAL 1 YEAR;
SQL
