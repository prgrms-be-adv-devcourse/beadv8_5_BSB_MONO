-- 운영 전용: 이미 만들어 둔 real-flow 크루로 rush.js의 .crews.json을 다시 만든다 (seed.js로 크루를 또 만들지 않는다)
-- 보조 서버 MySQL에서 돌리고, 나온 한 줄(JSON)을 부하 PC의 real-flow/.crews.json에 그대로 저장한다
--   mysql -N -B crewrun_member < seed/crews-prod.sql > .crews.json
-- rush.js는 크루 i가 계정 5i+1(크루장) ~ 5i+5라고 본다. 그 모양인 크루만 고르고, 같은 계정 묶음이 여러 번 만들어졌으면 가장 최근 크루를 쓴다
-- 크루 가입일은 seed/backdate-crews.sql로 당겨 둔 그대로다 (파티 신청은 크루 예매 시작 7일 전 가입이 필요하다)
SET SESSION group_concat_max_len = 1000000;

SELECT CONCAT('[', GROUP_CONCAT(
	JSON_OBJECT('crewId', t.crew_id, 'accounts', JSON_ARRAY(t.a, t.a + 1, t.a + 2, t.a + 3, t.a + 4))
	ORDER BY t.a SEPARATOR ','), ']')
FROM (
	SELECT MAX(g.crew_id) AS crew_id, g.a
	FROM (
		SELECT c.crew_id,
			MIN(acc.no) AS a,
			MAX(acc.no) AS z,
			COUNT(*) AS n,
			MIN(CASE WHEN cm.role = 'LEADER' THEN acc.no END) AS leader
		FROM crewrun_member.member_crew c
			JOIN crewrun_member.member_crew_member cm ON cm.crew_id = c.crew_id
			JOIN (
				SELECT member_id, CAST(SUBSTRING_INDEX(SUBSTRING(email, 9), '@', 1) AS UNSIGNED) AS no
				FROM crewrun_member.member_member
				WHERE email LIKE 'realflow%@crewrun.test'
			) acc ON acc.member_id = cm.member_id
		WHERE c.name LIKE '실흐름-%' AND c.status = 'ACTIVE'
		GROUP BY c.crew_id
	) g
	WHERE g.n = 5 AND g.z = g.a + 4 AND g.a % 5 = 1 AND g.leader = g.a
	GROUP BY g.a
) t;
