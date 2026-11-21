-- 탈퇴·비밀번호 재설정: 회원의 모든 family 기록, 목록 키, 배포 전 옛 키를 한 번에 지운다
-- 로그인과 겹쳐도 한 번에 실행되므로, 지우는 도중에 새 family가 끼어 목록에서 빠지지 않는다
-- KEYS[1] members:{memberId}:refreshTokenFamilies
-- KEYS[2] members:{memberId}:refreshToken (배포 전 옛 키)
-- ARGV[1] family 키 앞부분 members:{memberId}:refreshTokens:
for _, id in ipairs(redis.call('SMEMBERS', KEYS[1])) do
	redis.call('DEL', ARGV[1] .. id)
end
redis.call('DEL', KEYS[1], KEYS[2])
return 1
