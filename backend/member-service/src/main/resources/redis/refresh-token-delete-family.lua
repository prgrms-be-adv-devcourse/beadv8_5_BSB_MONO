-- 로그아웃: 들어온 토큰의 family 기록 하나만 지우고 목록에서도 뺀다. 같은 회원의 다른 기기 family는 그대로 둔다
-- KEYS[1] members:{memberId}:refreshTokens:{familyId}
-- KEYS[2] members:{memberId}:refreshTokenFamilies
-- ARGV[1] 들어온 토큰의 familyId
-- 돌려주는 값: 기록을 지웠으면 1, 이미 없었으면 0
redis.call('SREM', KEYS[2], ARGV[1])
return redis.call('DEL', KEYS[1])
