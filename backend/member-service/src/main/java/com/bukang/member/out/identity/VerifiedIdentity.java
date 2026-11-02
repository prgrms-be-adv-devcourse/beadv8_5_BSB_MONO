package com.bukang.member.out.identity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 포트원에서 서버가 직접 확인한 본인 정보 (BUK-33). 가입은 화면이 보낸 값 대신 이 값으로 회원을 만든다
 * CI 원문은 두지 않고 해시만 둔다. 로그에 개인정보가 남지 않게 toString은 이름·번호를 가린다
 *
 * @param phone 010-1234-5678 모양 (포트원은 숫자만 준다)
 * @param ciHash HMAC-SHA256(서버 비밀 키, CI)의 16진수 64자
 */
public record VerifiedIdentity(
	String name,
	LocalDate birthDate,
	String phone,
	String ciHash,
	LocalDateTime verifiedAt
) {
	@Override
	public String toString() {
		return "VerifiedIdentity[verifiedAt=" + verifiedAt + "]";
	}
}
