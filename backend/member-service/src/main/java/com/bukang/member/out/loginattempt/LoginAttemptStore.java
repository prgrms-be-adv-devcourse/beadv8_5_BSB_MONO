package com.bukang.member.out.loginattempt;

import java.time.Duration;

/**
 * 이메일별 로그인 시도 횟수를 저장한다 (BUK-185). 운영은 Redis, 테스트는 메모리 구현이다
 * 이메일은 원문이 아니라 SHA-256 해시로만 받는다
 */
public interface LoginAttemptStore {
	/**
	 * 시도 하나를 세고, 센 뒤의 횟수를 돌려준다. 비밀번호를 비교하기 전에 부른다
	 * 처음 세면 blockTime 만료를 걸고, maxFailures번째에서 만료를 blockTime으로 다시 맞춘다
	 */
	long record(String emailHash, int maxFailures, Duration blockTime);

	// 로그인에 성공하면 횟수를 지운다
	void clear(String emailHash);
}
