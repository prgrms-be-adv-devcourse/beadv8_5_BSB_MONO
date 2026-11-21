-- 로그인: 이 로그인(family)의 기록을 새로 만들고, 회원의 로그인 목록(Set)에 familyId를 넣는다
-- 다른 기기의 family는 건드리지 않는다. 기록 쓰기·TTL·목록 정리를 한 번에 해서 반쯤 쓴 기록을 다른 요청이 보지 않게 한다
-- 토큰 원문은 저장하지 않는다. Redis 내용이 새어도 서명된 토큰을 만들 수 없게 jti와 만료 시각만 둔다
-- KEYS[1] members:{memberId}:refreshTokens:{familyId}
-- KEYS[2] members:{memberId}:refreshTokenFamilies
-- ARGV[1] familyId  ARGV[2] jti  ARGV[3] 발급 시각(초)  ARGV[4] family 최종 만료(초)  ARGV[5] 토큰 만료(초, TTL)
-- ARGV[6] family 키 앞부분 members:{memberId}:refreshTokens:
redis.call('HSET', KEYS[1],
	'currentJti', ARGV[2],
	'currentExpiresAt', ARGV[5],
	'previousJti', '',
	'rotatedAt', ARGV[3],
	'familyExpiresAt', ARGV[4])
redis.call('EXPIREAT', KEYS[1], ARGV[5])

-- 키가 이미 사라진(만료된) 로그인이 목록에 쌓이지 않게 함께 뺀다
-- shortcut: family 키 이름을 ARGV로 만들어 읽으므로 Redis Cluster에서는 쓸 수 없다, 클러스터로 옮길 때 해시 태그로 키를 맞춘다
for _, id in ipairs(redis.call('SMEMBERS', KEYS[2])) do
	if redis.call('EXISTS', ARGV[6] .. id) == 0 then
		redis.call('SREM', KEYS[2], id)
	end
end
redis.call('SADD', KEYS[2], ARGV[1])
-- 목록은 가장 늦게 끝나는 family까지 남아야 하므로 TTL을 늘릴 때만 바꾼다 (최대 기간 설정을 줄여 배포해도 줄지 않는다)
-- EXPIREAT ... GT는 TTL이 없는 새 목록을 무한으로 보아 만료를 걸지 않으므로 쓰지 않는다
local ttl = redis.call('TTL', KEYS[2])
if ttl < 0 or tonumber(ARGV[3]) + ttl < tonumber(ARGV[4]) then
	redis.call('EXPIREAT', KEYS[2], ARGV[4])
end
return 1
