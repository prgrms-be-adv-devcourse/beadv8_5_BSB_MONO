-- 대기열 등록: 이미 입장(active)한 회원인지 확인하고, 아니면 줄(waiting)에 넣는다
-- 확인과 추가를 한 번에 해서, 그 사이에 승격(waiting → active)이 끼어들지 못하게 한다
-- 개인 줄과 파티 줄이 이 스크립트를 함께 쓴다. QueueRedisRepository가 줄 종류(QueueLine)에 맞는 키를 넘긴다
--
-- KEYS (줄 종류마다 다름)
--   |         | 개인 줄 (INDIVIDUAL)                          | 파티 줄 (PARTY)                                      |
--   | KEYS[1] | race:{raceId}:racecourse:{raceCourseId}:waiting | race:{raceId}:racecourse:{raceCourseId}:party:waiting |
--   | KEYS[2] | race:{raceId}:racecourse:{raceCourseId}:active  | race:{raceId}:racecourse:{raceCourseId}:party:active  |
--   | KEYS[3] | queue:waiting                                   | queue:party:waiting                                   |
--   KEYS[1] 기다리는 줄 (ZSet, 값 = 회원 ID, score = 등록 시각)
--   KEYS[2] 입장한 줄 (ZSet, 값 = 회원 ID, score = 입장 끝 시각)
--   KEYS[3] 스케줄러가 승격할 대기열 목록 (Set, 값 = {raceId}:{raceCourseId})
--
-- ARGV (두 줄 같음)
--   ARGV[1] 회원 ID. 개인 줄은 예매하는 회원, 파티 줄은 파티장 회원 ID다 (파티 ID가 아니다)
--   ARGV[2] 지금 (epoch ms)
--   ARGV[3] {raceId}:{raceCourseId} (KEYS[3] 목록에 넣을 값)
--
-- 돌려주는 값: { 'ACTIVE' } 또는 { 'WAITING', 내 앞 인원(0부터), 줄 전체 인원 }
local activeUntil = redis.call('ZSCORE', KEYS[2], ARGV[1])
if activeUntil and tonumber(activeUntil) > tonumber(ARGV[2]) then
	return { 'ACTIVE' }  -- 끝 시각이 지나지 않은 active. 끝 시각이 지났으면 스케줄러가 지우기 전이라도 다시 줄을 설 수 있다
end

redis.call('ZADD', KEYS[1], 'NX', ARGV[2], ARGV[1])  -- 이미 줄에 있으면(다른 기기 등) 처음 순번을 유지한다
redis.call('SADD', KEYS[3], ARGV[3])  -- 스케줄러가 승격할 대기열 목록. 이미 들어 있으면 그대로다
return { 'WAITING', redis.call('ZRANK', KEYS[1], ARGV[1]), redis.call('ZCARD', KEYS[1]) }
