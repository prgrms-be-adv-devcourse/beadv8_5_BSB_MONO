-- 대기열 승격: 끝 시각이 지난 active를 지우고, waiting 앞에서부터 정해진 인원을 active로 옮긴다
-- 정리·승격·목록 정리를 한 번에 해서, 그 사이에 등록 Lua가 끼어들지 못하게 한다
-- 개인 줄과 파티 줄이 이 스크립트를 함께 쓴다. QueueRedisRepository가 줄 종류(QueueLine)에 맞는 키를 넘기고,
-- QueuePromoteUseCase가 줄 종류마다 승격할 인원·입장 시간을 정해 ARGV로 넘긴다
--
-- KEYS (줄 종류마다 다름)
--   |         | 개인 줄 (INDIVIDUAL)                          | 파티 줄 (PARTY)                                      |
--   | KEYS[1] | race:{raceId}:racecourse:{raceCourseId}:waiting | race:{raceId}:racecourse:{raceCourseId}:party:waiting |
--   | KEYS[2] | race:{raceId}:racecourse:{raceCourseId}:active  | race:{raceId}:racecourse:{raceCourseId}:party:active  |
--   | KEYS[3] | queue:waiting                                   | queue:party:waiting                                   |
--   KEYS[1] 기다리는 줄 (ZSet, 값 = 회원 ID, score = 등록 시각). 개인 줄은 예매하는 회원, 파티 줄은 파티장 회원 ID다
--   KEYS[2] 입장한 줄 (ZSet, 값 = 회원 ID, score = 입장 끝 시각)
--   KEYS[3] 스케줄러가 승격할 대기열 목록 (Set, 값 = {raceId}:{raceCourseId})
--
-- ARGV (형식은 두 줄 같음, 승격할 인원·입장 끝 시각은 줄 종류별 설정값)
--   ARGV[1] 지금 (epoch ms)
--   ARGV[2] 승격할 인원. 개인 줄은 회원 수, 파티 줄은 파티 수(파티장 수)다
--   ARGV[3] 승격한 회원의 입장 끝 시각 (epoch ms) = 지금 + 줄 종류별 입장 시간
--   ARGV[4] {raceId}:{raceCourseId} (KEYS[3] 목록에서 뺄 값)
--
-- 돌려주는 값: { 지운 active 인원, 승격한 인원 }

-- 끝 시각이 지난 active를 지운다 (끝 시각 <= 지금. 등록·순번 조회의 "끝 시각 > 지금이면 입장"과 같은 경계)
local expired = redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[1])

-- waiting 앞에서부터 꺼내(꺼낸 회원은 waiting에서 지워진다) active에 끝 시각으로 넣는다
-- ZPOPMIN은 { 회원, score, 회원, score, ... }를 돌려주므로 홀수 칸의 회원만 쓴다
local popped = redis.call('ZPOPMIN', KEYS[1], ARGV[2])
for i = 1, #popped, 2 do
	redis.call('ZADD', KEYS[2], ARGV[3], popped[i])
end

-- waiting과 active가 모두 비면 스케줄러 목록에서 뺀다. active가 남아 있으면 그 만료 정리를 위해 둔다
-- 누가 다시 등록하면 등록 Lua가 SADD로 다시 넣는다
if redis.call('ZCARD', KEYS[1]) == 0 and redis.call('ZCARD', KEYS[2]) == 0 then
	redis.call('SREM', KEYS[3], ARGV[4])
end

return { expired, #popped / 2 }
