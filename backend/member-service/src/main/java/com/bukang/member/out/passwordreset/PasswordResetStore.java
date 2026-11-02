package com.bukang.member.out.passwordreset;

import java.time.Duration;
import java.util.Optional;

/**
 * 비밀번호 재설정 링크와 요청 횟수를 저장한다 (BUK-40). 운영은 Redis, 테스트는 메모리 구현이다
 * 링크 토큰은 원문이 아니라 SHA-256 해시로만 받는다. 저장소 내용이 새어도 링크를 만들 수 없다
 */
public interface PasswordResetStore {
	/**
	 * 같은 이메일의 요청을 하나 센다. 마지막 요청에서 cooldown이 지나지 않았거나 window 안에서 dailyLimit를 넘으면 거짓
	 */
	boolean tryAcquireRequest(String emailHash, Duration cooldown, int dailyLimit, Duration window);

	// 같은 IP의 요청을 하나 센다. window 안에서 dailyLimit를 넘으면 거짓
	boolean tryAcquireIpRequest(String ipHash, int dailyLimit, Duration window);

	/**
	 * 회원의 새 링크를 저장하고 이전 링크를 지운다. 회원당 쓸 수 있는 링크는 가장 최근 것 하나다
	 */
	void saveToken(long memberId, String tokenHash, Duration ttl);

	// 다시 보내기 전에 이 링크가 아직 그 회원의 최신 링크인지 확인한다
	boolean isLatestToken(long memberId, String tokenHash);

	// 링크가 아직 쓸 수 있는지 본다. 지우지 않는다
	Optional<Long> findMemberId(String tokenHash);

	// 꺼내면서 지운다. 같은 링크로 동시에 두 번 바꾸려 해도 하나만 회원 ID를 받는다
	Optional<Long> takeMemberId(String tokenHash);

	// 비밀번호를 바꾼 뒤 회원의 최신 링크 기록을 지운다
	void deleteLatestToken(long memberId);
}
