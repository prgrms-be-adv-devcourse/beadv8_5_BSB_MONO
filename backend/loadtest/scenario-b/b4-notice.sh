#!/usr/bin/env bash
# B-4 카드 취소 기한 알림 배치를 한 번 재서 걸린 시간과 SQL 수를 남긴다
# 실행(backend/에서): ./loadtest/scenario-b/b4-notice.sh 1000
#
# 준비: cash를 cron을 바꿔 띄운다. 배치는 원래 매일 09:00이라 매분 0초에 돌게 덮어쓴다
#   source loadtest/scenario-b/run-env.sh
#   CASH_WITHDRAWAL_DEADLINE_NOTICE_CRON='0 * * * * *' ./gradlew :cash-service:bootRun
#
# 걸린 시간: Spring이 @Scheduled 실행마다 남기는 tasks_scheduled_execution_seconds (알림 Kafka 전송 포함)
# SQL 수: performance_schema의 문장 요약을 비운 뒤 배치 한 번 동안 쌓인 cash_ 표 문장을 센다
#   같은 DB를 쓰는 다른 서비스와 1분마다 도는 충전 재확인도 섞이므로, 회원 수만큼 늘어나는 문장을 본다
set -euo pipefail
cd "$(dirname "$0")"
N="${1:?회원 수를 넣어 주세요. 예: ./b4-notice.sh 1000}"
CASH_URL="${CASH_URL:-http://localhost:8081}"
METRIC='tasks_scheduled_execution_seconds_(count|sum)\{.*code_function="notifyDeadlines".*outcome="SUCCESS"'

read_metric() {
	curl -sf "${CASH_URL}/actuator/prometheus" | grep -E "$METRIC" | grep "_$1{" | awk '{print $NF}' || true
}

./seed-notice.sh "$N"

before_count="$(read_metric count)"; before_count="${before_count:-0}"
before_sum="$(read_metric sum)"; before_sum="${before_sum:-0}"

# 다음 0초 2초 전에 비워 배치 한 번 몫만 쌓이게 한다 (0초에 맞추면 배치가 먼저 끝날 수 있다)
sec="$(date +%S | sed 's/^0//')"
sleep $(( (118 - sec) % 60 ))
echo "TRUNCATE TABLE performance_schema.events_statements_summary_by_digest;" | ./db.sh
echo "배치를 기다려요 ($(date +%T))"

until [[ "$(read_metric count)" != "${before_count}" && -n "$(read_metric count)" ]]; do sleep 1; done

after_sum="$(read_metric sum)"
echo
echo "== B-4 결과 (대상 ${N}명) =="
awk -v a="$after_sum" -v b="$before_sum" 'BEGIN { printf "걸린 시간: %.3f초\n", a - b }'

./db.sh <<'SQL'
SELECT SUM(COUNT_STAR) AS cash_문장_수, ROUND(SUM(SUM_TIMER_WAIT) / 1e9, 1) AS DB_시간_ms
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME = 'bukang-db' AND DIGEST_TEXT LIKE '%cash\_%' AND DIGEST_TEXT NOT LIKE '%performance_schema%';

SELECT COUNT_STAR AS 횟수, ROUND(SUM_TIMER_WAIT / 1e9, 1) AS 합_ms, LEFT(DIGEST_TEXT, 110) AS 문장
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME = 'bukang-db' AND DIGEST_TEXT LIKE '%cash\_%' AND DIGEST_TEXT NOT LIKE '%performance_schema%'
ORDER BY COUNT_STAR DESC LIMIT 8;
SQL
