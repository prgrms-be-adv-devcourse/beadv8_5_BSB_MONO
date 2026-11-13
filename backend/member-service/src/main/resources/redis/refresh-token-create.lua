-- 로그인: 회원의 Refresh Token 기록을 새 family로 통째로 바꾼다 (다른 기기의 이전 family는 여기서 사라진다)
-- 지우고 쓰고 TTL 거는 일을 한 번에 해서, 그 사이에 들어온 재발급이 반쯤 쓴 기록을 보지 않게 한다
-- 토큰 원문은 저장하지 않는다. Redis 내용이 새어도 서명된 토큰을 만들 수 없게 jti와 만료 시각만 둔다
-- KEYS[1] members:{memberId}:refreshToken
-- ARGV[1] familyId  ARGV[2] jti  ARGV[3] 발급 시각(초)  ARGV[4] family 최종 만료(초)  ARGV[5] 토큰 만료(초, TTL)
redis.call('DEL', KEYS[1])
redis.call('HSET', KEYS[1],
	'familyId', ARGV[1],
	'currentJti', ARGV[2],
	'currentExpiresAt', ARGV[5],
	'previousJti', '',
	'rotatedAt', ARGV[3],
	'familyExpiresAt', ARGV[4])
redis.call('EXPIREAT', KEYS[1], ARGV[5])
return 1
