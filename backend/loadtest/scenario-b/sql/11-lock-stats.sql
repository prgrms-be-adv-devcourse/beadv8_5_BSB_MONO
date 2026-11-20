-- InnoDB 락 카운터. MySQL이 켜진 뒤로 쌓인 값이라 실행 전후로 한 번씩 보고 차이를 쓴다
-- (Grafana 「MySQL 락 대기」 패널은 같은 값을 초당으로 보여 준다)
SELECT NAME AS 이름, COUNT AS 누적,
	CASE NAME
		WHEN 'lock_deadlocks' THEN '데드락 (늘면 안 됨)'
		WHEN 'lock_timeouts' THEN '락 대기 시간 초과 (늘면 안 됨)'
		WHEN 'lock_row_lock_waits' THEN '행 락을 기다린 횟수 (줄 서기)'
		WHEN 'lock_row_lock_time' THEN '행 락을 기다린 시간 합 (ms)'
		WHEN 'lock_row_lock_time_max' THEN '가장 오래 기다린 시간 (ms)'
	END AS 뜻
FROM information_schema.INNODB_METRICS
WHERE NAME IN ('lock_deadlocks', 'lock_timeouts', 'lock_row_lock_waits', 'lock_row_lock_time',
	'lock_row_lock_time_max');
