package com.bukang.member.out.identity;

/**
 * 포트원 본인인증 결과를 조회한다 (BUK-33). 테스트는 이 포트를 목으로 바꿔 포트원을 부르지 않는다
 */
public interface PortOneIdentityClient {
	/**
	 * @throws com.bukang.member.domain.exception.IdentityVerificationFailedException 포트원에 그 인증 ID가 없다
	 * @throws com.bukang.member.domain.exception.IdentityVerificationUnavailableException 포트원에 닿지 못했다
	 */
	PortOneIdentityVerification find(String identityVerificationId);
}
