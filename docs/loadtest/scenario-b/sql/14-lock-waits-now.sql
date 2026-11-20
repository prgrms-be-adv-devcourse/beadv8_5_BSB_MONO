-- 지금 행 잠금을 기다리는 것 (run.sh가 시나리오 동안 5초마다 찍어 <시나리오>.lock-waits.tsv에 쌓는다)
-- 순간을 찍어서 5초보다 짧은 대기는 놓칠 수 있다. 놓친 것까지 합은 15-lock-time-by-sql.sql이 본다
-- 같은 테이블·인덱스·잠금 모양끼리 묶어 몇 개가 기다리는지, 가장 오래 기다린 시간, 기다린 SQL과 막은 SQL 예시를 남긴다
-- 막은 SQL이 비면(NULL) 막은 쪽 트랜잭션이 SQL 사이(앱 코드 실행·다른 호출 대기)에서 잠금을 쥐고 있다는 뜻이다
SELECT NOW(3) AS 시각, locked_table AS 테이블, locked_index AS 인덱스, waiting_lock_mode AS 기다린_잠금,
	COUNT(*) AS 기다리는_수, MAX(wait_age_secs) AS 가장_오래_초,
	LEFT(ANY_VALUE(waiting_query), 100) AS 기다린_SQL, LEFT(ANY_VALUE(blocking_query), 100) AS 막은_SQL
FROM sys.innodb_lock_waits
GROUP BY locked_table, locked_index, waiting_lock_mode;
