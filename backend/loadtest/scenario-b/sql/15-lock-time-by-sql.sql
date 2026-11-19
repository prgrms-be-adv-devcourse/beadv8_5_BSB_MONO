-- 이 시나리오 동안 SQL마다 잠금을 기다린 시간 합 (run.sh가 시나리오 전에 문장 요약을 비우고, 끝나면 읽어 <시나리오>.lock-sql.txt에 남긴다)
-- SUM_LOCK_TIME에는 InnoDB 행 잠금 대기가 들어간다. MySQL 8.4에서 확인: 다른 세션이 3초 잡은 행을 바꾼 UPDATE가 약 2.3초로 잡힘
-- 측정 도구가 부른 SQL(잠금 스냅샷, 문장 요약 비우기)은 뺀다
SELECT COUNT_STAR AS 횟수, ROUND(SUM_LOCK_TIME / 1e9, 1) AS 잠금_대기_합_ms,
	ROUND(SUM_LOCK_TIME / COUNT_STAR / 1e9, 2) AS 한_번_평균_ms, ROUND(MAX_TIMER_WAIT / 1e9, 1) AS 가장_오래_ms,
	ROUND(SUM_TIMER_WAIT / 1e9, 1) AS 전체_시간_합_ms, LEFT(DIGEST_TEXT, 120) AS 문장
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME = 'bukang-db' AND SUM_LOCK_TIME > 0
	AND DIGEST_TEXT NOT LIKE '%innodb_lock_waits%' AND DIGEST_TEXT NOT LIKE '%performance_schema%'
ORDER BY SUM_LOCK_TIME DESC
LIMIT 15;
