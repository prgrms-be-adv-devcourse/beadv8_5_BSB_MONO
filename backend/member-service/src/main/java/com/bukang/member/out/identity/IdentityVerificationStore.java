package com.bukang.member.out.identity;

import java.util.Optional;

/**
 * 본인인증 도중의 짧은 상태를 저장한다 (BUK-33). 운영은 Redis(RedisIdentityVerificationStore), 테스트는 메모리 구현이다
 * 대기: 서버가 만든 인증 ID. 이 ID로만 인증 결과를 확인해, 다른 곳에서 만든 인증 ID로 가입하는 일을 막는다
 * 결과: 서버가 포트원에서 확인한 본인 정보. 가입이 커밋되면 지운다
 */
public interface IdentityVerificationStore {
	void savePending(String identityVerificationId);

	boolean isPending(String identityVerificationId);

	// 결과를 저장하고 대기를 지운다
	void saveVerified(String identityVerificationId, VerifiedIdentity identity);

	Optional<VerifiedIdentity> findVerified(String identityVerificationId);

	void deleteVerified(String identityVerificationId);
}
