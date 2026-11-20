-- 스키마 드리프트 검사용 "사실" 추출 (BUK, 배포 뒤 인덱스·컬럼·FK 비교)
-- 정답 DB(러너의 빈 MySQL)와 라이브 DB(data-node bukang-db)에서 똑같이 돌려, 줄 단위로 정렬해 diff 한다.
-- 운영은 서비스마다 DB가 따로다(crewrun_<서비스>, BUK-238). flyway 기록표는 비교에서 뺀다.
-- 운영 MySQL은 8.4, ddl-auto=validate라 Flyway가 유일한 스키마 소스이므로 순수 Flyway 결과와 맞는다.
-- 예외: file-service의 Spring Batch 표(BATCH_)는 initialize-schema: always가 Flyway 밖에서 만든다.
-- 정답 DB에는 없어 배포 때마다 "라이브에만 있음"으로 잡히므로 비교에서 뺀다 (리뷰 #695).
SELECT line FROM (
	-- 인덱스: 유니크 여부(NON_UNIQUE)·타입·컬럼을 SEQ 순서로. 유니크 제약과 PK도 여기 인덱스로 나온다
	SELECT CONCAT_WS('\t', 'IDX', TABLE_SCHEMA, TABLE_NAME, INDEX_NAME, NON_UNIQUE, INDEX_TYPE,
			GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',')) AS line
		FROM information_schema.STATISTICS
		WHERE TABLE_SCHEMA LIKE 'crewrun\_%' AND TABLE_NAME NOT LIKE 'flyway\_schema\_history%'
			AND TABLE_NAME NOT LIKE 'BATCH\_%'
		GROUP BY TABLE_SCHEMA, TABLE_NAME, INDEX_NAME, NON_UNIQUE, INDEX_TYPE
	UNION ALL
	-- 컬럼: 타입(길이·unsigned·enum 목록 포함)과 NULL 허용. int→bigint·enum 변경 드리프트를 잡는다
	SELECT CONCAT_WS('\t', 'COL', TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE)
		FROM information_schema.COLUMNS
		WHERE TABLE_SCHEMA LIKE 'crewrun\_%' AND TABLE_NAME NOT LIKE 'flyway\_schema\_history%'
			AND TABLE_NAME NOT LIKE 'BATCH\_%'
	UNION ALL
	-- 외래 키: 제약 이름은 자동 생성이라 빼고, 거는 컬럼과 가리키는 대상으로 비교한다
	SELECT CONCAT_WS('\t', 'FK', TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME)
		FROM information_schema.KEY_COLUMN_USAGE
		WHERE TABLE_SCHEMA LIKE 'crewrun\_%' AND REFERENCED_TABLE_NAME IS NOT NULL
			AND TABLE_NAME NOT LIKE 'flyway\_schema\_history%' AND TABLE_NAME NOT LIKE 'BATCH\_%'
) t
ORDER BY line;
