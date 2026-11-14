package com.bukang.member.out.emailcheck;

import java.time.Duration;

// 이메일 중복 확인 요청 횟수를 IP별로 센다. 운영은 Redis, 테스트는 메모리 구현이다
public interface EmailCheckStore {
	// 같은 IP의 요청을 하나 센다. window 안에서 limit를 넘으면 거짓
	boolean tryAcquire(String ipHash, int limit, Duration window);
}
