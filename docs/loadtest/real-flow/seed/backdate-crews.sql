-- real-flow 크루의 가입 시각을 30일 전으로 당긴다
-- 파티 신청은 파티원 모두 크루 예매 시작 7일 전까지 크루에 가입했어야 한다 (주문 RaceCourseInfo, 11/05 회의 P4)
-- 로컬: docker exec -i bukang-db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" bukang-db' < seed/backdate-crews.sql
-- 운영: 보조 서버 MySQL의 crewrun_member DB에서 돌린다
UPDATE member_crew_member cm
	JOIN member_crew c ON c.crew_id = cm.crew_id
SET cm.joined_at = NOW() - INTERVAL 30 DAY
WHERE c.name LIKE '실흐름-%';

SELECT COUNT(*) AS `가입 시각을 당긴 크루원` FROM member_crew_member cm
	JOIN member_crew c ON c.crew_id = cm.crew_id
WHERE c.name LIKE '실흐름-%' AND cm.joined_at < NOW() - INTERVAL 7 DAY;
