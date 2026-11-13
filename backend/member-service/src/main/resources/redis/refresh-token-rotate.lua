-- 재발급: 들어온 토큰이 지금 쓸 수 있는 것인지 판정하고, 정상이면 새 토큰으로 바꾼다
-- 판정과 교체를 한 번에 해서, 같은 토큰으로 동시에 온 두 요청이 둘 다 정상으로 통과하지 못하게 한다
-- KEYS[1] members:{memberId}:refreshToken
-- ARGV[1] 들어온 토큰의 familyId  ARGV[2] 들어온 토큰의 jti
-- ARGV[3] 새 jti  ARGV[4] 지금(초)  ARGV[5] 유예(초)  ARGV[6] 새 토큰 만료(초, TTL)
-- 돌려주는 값: { 판정 } 또는 GRACE면 { 'GRACE', 지금 토큰 jti, 지금 토큰 만료(초) }
if redis.call('EXISTS', KEYS[1]) == 0 then
	return { 'MISSING' }  -- 로그아웃, 탈취 처리, TTL 만료
end

local saved = redis.call('HMGET', KEYS[1], 'familyId', 'currentJti', 'previousJti', 'rotatedAt', 'currentExpiresAt')
local familyId, currentJti, previousJti, rotatedAt, currentExpiresAt = saved[1], saved[2], saved[3], saved[4], saved[5]

if familyId ~= ARGV[1] then
	return { 'STALE' }  -- 다른 기기에서 새로 로그인해 family가 바뀌었다. 지금 family는 건드리지 않는다
end

if currentJti == ARGV[2] then
	redis.call('HSET', KEYS[1], 'previousJti', currentJti, 'currentJti', ARGV[3], 'currentExpiresAt', ARGV[6], 'rotatedAt', ARGV[4])
	redis.call('HDEL', KEYS[1], 'currentToken')  -- 배포 전에 저장된 토큰 원문이 남지 않게 지운다
	redis.call('EXPIREAT', KEYS[1], ARGV[6])
	return { 'ROTATED' }
end

if previousJti == ARGV[2] and tonumber(ARGV[4]) - tonumber(rotatedAt) <= tonumber(ARGV[5]) then
	if not currentExpiresAt then
		return { 'MISSING' }  -- 배포 전에 만든 기록이라 만료 시각이 없다. 옛 원문은 읽지 않고 다시 로그인하게 한다
	end
	return { 'GRACE', currentJti, currentExpiresAt }  -- 방금 회전된 토큰이 유예 안에 다시 왔다. 서비스가 지금 토큰을 다시 서명해 돌려준다
end

redis.call('DEL', KEYS[1])  -- 같은 family의 이미 쓴 토큰: 누가 진짜인지 모르므로 family를 끊는다
return { 'THEFT' }
