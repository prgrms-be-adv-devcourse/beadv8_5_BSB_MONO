-- 대회 대표 이미지를 종목(RaceCourse)에서 대회(Race)로 옮긴다 (CRU-6)
-- 대표 이미지가 종목마다 1장에서 대회마다 1장으로 바뀌어, 이미 종목에 붙은 대표 이미지를 대회에 다시 붙인다
-- 파일 표(crewrun_file.file_file)와 종목 표(crewrun_product.race_course)가 다른 DB라
-- 서비스 코드나 Flyway로는 할 수 없고, 같은 MySQL 서버에서 사람이 한 번 실행한다
--
-- 무엇을 바꾸나
--   대회마다 첫 종목(종목 ID가 가장 작은 것)의 대표 이미지 1장 → ref_type 'Race', ref_id 대회 ID, sort_no 0
--   같은 대회의 나머지 종목 대표 이미지 → status 'DELETED' (file-service 정리 배치가 1일 뒤 DB 행과 S3 객체를 지운다)
--   이미 대회에 대표 이미지가 붙은 대회는 대회 것을 그대로 두고, 종목에 남은 대표 이미지만 지운다
--   종목에 붙은 ACTIVE 대표 이미지만 대상이라 두 번 실행해도 더 바뀌는 행이 없다
--
-- 언제 실행하나
--   CRU-6 백엔드(product-service)를 배포한 뒤에 실행한다
--   먼저 실행하면 옛 코드가 대회를 수정할 때 대표 이미지를 다시 종목에 붙인다
--   프론트가 대표 이미지를 대회(refType=Race)에서 읽기 전까지는 화면에서 대표 이미지가 보이지 않는다
--
-- 실행 전 백업 (보조 EC2, 저장소 루트 /opt/crewrun 에서)
--   sudo docker exec bukang-db sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction crewrun_file file_file' \
--     | gzip > ~/file_file-before-cru6-$(date -u +%Y%m%d-%H%M).sql.gz
--
-- 미리보기만 하려면 아래 ① 블록(임시 표 만들기와 SELECT)까지만 실행하고 ROLLBACK 한다
--
-- 실행
--   sudo docker exec -i bukang-db sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD"' \
--     < infra/data-node/once/20261117-race-thumbnail-to-race.sql
--
-- 되돌리기: 위 백업을 복원한다 (file_file 표만 덮어쓴다)
--   gunzip -c ~/file_file-before-cru6-<시각>.sql.gz \
--     | sudo docker exec -i bukang-db sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" crewrun_file'

USE crewrun_file;

START TRANSACTION;

-- ① 옮길 대상: 종목에 붙은 ACTIVE 대표 이미지에 대회 ID와 대회 안의 순번을 붙인다
--    순번 1(첫 종목)이고 대회에 대표 이미지가 아직 없으면 대회로 옮기고(move = 1), 나머지는 지운다(move = 0)
CREATE TEMPORARY TABLE race_thumbnail_move AS
SELECT ranked.file_id,
	ranked.race_id,
	(ranked.rn = 1 AND NOT EXISTS (
		SELECT 1
		FROM crewrun_file.file_file r
		WHERE r.ref_type = 'Race'
			AND r.ref_id = ranked.race_id
			AND r.file_type = 'THUMBNAIL'
			AND r.status = 'ACTIVE'
	)) AS move
FROM (
	SELECT f.id AS file_id,
		c.race_id,
		ROW_NUMBER() OVER (PARTITION BY c.race_id ORDER BY c.race_course_id, f.id) AS rn
	FROM crewrun_file.file_file f
		JOIN crewrun_product.race_course c ON c.race_course_id = f.ref_id
	WHERE f.ref_type = 'RaceCourse'
		AND f.file_type = 'THUMBNAIL'
		AND f.status = 'ACTIVE'
) ranked;

-- 미리보기: 대회로 옮길 건수, 지울 건수, 관련 대회 수
SELECT COALESCE(SUM(move = 1), 0) AS move_to_race,
	COALESCE(SUM(move = 0), 0) AS delete_extra,
	COUNT(DISTINCT race_id) AS races
FROM race_thumbnail_move;

-- ② 첫 종목의 대표 이미지를 대회에 붙인다
UPDATE crewrun_file.file_file f
	JOIN race_thumbnail_move m ON m.file_id = f.id AND m.move = 1
SET f.ref_type = 'Race',
	f.ref_id = m.race_id,
	f.sort_no = 0,
	f.modify_date = NOW(6);

-- ③ 나머지 종목 대표 이미지는 지운다 (대표 이미지는 대회마다 1장)
UPDATE crewrun_file.file_file f
	JOIN race_thumbnail_move m ON m.file_id = f.id AND m.move = 0
SET f.status = 'DELETED',
	f.modify_date = NOW(6);

-- 확인 1: 종목에 남은 ACTIVE 대표 이미지 (0이어야 한다. 종목이 지워진 파일은 대상이 아니라 남을 수 있다)
SELECT COUNT(*) AS course_thumbnails_left
FROM crewrun_file.file_file f
	JOIN crewrun_product.race_course c ON c.race_course_id = f.ref_id
WHERE f.ref_type = 'RaceCourse' AND f.file_type = 'THUMBNAIL' AND f.status = 'ACTIVE';

-- 확인 2: 대표 이미지가 2장 이상인 대회 (없어야 한다)
SELECT ref_id AS race_id, COUNT(*) AS thumbnails
FROM crewrun_file.file_file
WHERE ref_type = 'Race' AND file_type = 'THUMBNAIL' AND status = 'ACTIVE'
GROUP BY ref_id
HAVING COUNT(*) > 1;

COMMIT;

DROP TEMPORARY TABLE race_thumbnail_move;
