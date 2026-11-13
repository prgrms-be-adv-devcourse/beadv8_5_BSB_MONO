-- 로그인 시도 하나를 센다. 올리기와 만료 걸기를 한 번에 해서, 만료 없는 키가 남지 않게 한다
-- KEYS[1] login-attempts:{이메일 해시}:failCount
-- ARGV[1] 최대 실패 횟수
-- ARGV[2] 차단 시간(밀리초)
-- 돌려주는 값: 센 뒤의 횟수
local count = redis.call('INCR', KEYS[1])
-- 처음 셀 때와 마지막 허용 횟수(5번째)에서 만료를 건다. 만료가 사라진 키도 다시 건다
if count == 1 or count == tonumber(ARGV[1]) or redis.call('PTTL', KEYS[1]) < 0 then
	redis.call('PEXPIRE', KEYS[1], ARGV[2])
end
return count
