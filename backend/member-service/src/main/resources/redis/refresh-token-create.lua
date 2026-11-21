-- 로그인: 이 로그인(family)의 기록을 새로 만들고, 회원의 로그인 목록(Sorted Set)에 familyId를 넣는다
-- 다른 기기의 family는 건드리지 않는다. 기록 쓰기·TTL·목록 정리를 한 번에 해서 반쯤 쓴 기록을 다른 요청이 보지 않게 한다
-- 토큰 원문은 저장하지 않는다. Redis 내용이 새어도 서명된 토큰을 만들 수 없게 jti와 만료 시각만 둔다
-- KEYS[1] members:{memberId}:refreshTokens:{familyId}
-- KEYS[2] members:{memberId}:refreshTokenFamilies (Sorted Set, 점수 = family 최종 만료)
-- ARGV[1] familyId  ARGV[2] jti  ARGV[3] 발급 시각(초)  ARGV[4] family 최종 만료(초)  ARGV[5] 토큰 만료(초, TTL)
redis.call('HSET', KEYS[1],
	'currentJti', ARGV[2],
	'currentExpiresAt', ARGV[5],
	'previousJti', '',
	'rotatedAt', ARGV[3],
	'familyExpiresAt', ARGV[4])
redis.call('EXPIREAT', KEYS[1], ARGV[5])

-- 목록은 Sorted Set이고 점수는 family 최종 만료(초)다. 이미 최종 만료가 지난 로그인만 한 번에 뺀다
-- (목록 전체를 읽지 않아, 로그인이 많이 쌓여도 로그인 한 번의 비용이 거의 일정하다)
redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[3])
redis.call('ZADD', KEYS[2], ARGV[4], ARGV[1])
-- 목록은 가장 늦게 끝나는 family까지 남아야 하므로 TTL을 늘릴 때만 바꾼다 (최대 기간 설정을 줄여 배포해도 줄지 않는다)
-- EXPIREAT ... GT는 TTL이 없는 새 목록을 무한으로 보아 만료를 걸지 않으므로 쓰지 않는다
local ttl = redis.call('TTL', KEYS[2])
if ttl < 0 or tonumber(ARGV[3]) + ttl < tonumber(ARGV[4]) then
	redis.call('EXPIREAT', KEYS[2], ARGV[4])
end
return 1
