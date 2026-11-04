package com.bukang.member.out.identity;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 포트원 본인인증 단건 조회 결과에서 가입에 쓰는 값만 꺼낸 것 (BUK-33)
 * 조회 API: GET /identity-verifications/{identityVerificationId} (포트원 V2 OpenAPI getIdentityVerification)
 *
 * @param status READY(대기), VERIFIED(완료), FAILED(실패). VERIFIED일 때만 아래 고객 정보가 있다
 * @param phoneNumber 숫자만 (예: 01012345678)
 * @param ci 카카오 인증이면 없다
 */
public record PortOneIdentityVerification(
	String id,
	String status,
	String name,
	LocalDate birthDate,
	String phoneNumber,
	String ci,
	Instant verifiedAt
) {
	public static final String VERIFIED = "VERIFIED";

	public boolean isVerified() {
		return VERIFIED.equals(status);
	}

	@Override
	public String toString() {
		return "PortOneIdentityVerification[id=" + id + ", status=" + status + "]";
	}
}
