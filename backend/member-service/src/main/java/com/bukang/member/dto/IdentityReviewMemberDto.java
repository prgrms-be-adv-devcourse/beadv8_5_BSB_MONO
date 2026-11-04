package com.bukang.member.dto;

import java.time.LocalDateTime;

import com.bukang.member.domain.Member;

import io.swagger.v3.oas.annotations.media.Schema;

// 확인이 필요한 계정의 회원 한 명. 개인정보는 가린 값만 담고 생년월일은 담지 않는다 (BE-038 결정 4)
@Schema(description = "확인이 필요한 계정의 회원 한 명 (개인정보는 가린 값)")
public record IdentityReviewMemberDto(
	@Schema(description = "회원 ID", example = "12")
	long id,
	@Schema(description = "닉네임", example = "러너12")
	String nickname,
	@Schema(description = "가린 이름 (첫 글자와 끝 글자만)", example = "홍*동")
	String maskedName,
	@Schema(description = "가린 휴대폰 번호 (앞 3자리와 뒤 4자리만)", example = "010-****-5678")
	String maskedPhone,
	@Schema(description = "가린 이메일 (아이디 앞 2글자와 도메인만)", example = "ho***@crewrun.com")
	String maskedEmail,
	@Schema(description = "CI를 주는 본인인증으로 가입했으면 true, 카카오처럼 CI가 없으면 false", example = "false")
	boolean ciVerified,
	@Schema(description = "가입 시각", example = "2026-11-02T09:00:00")
	LocalDateTime joinedAt
) {
	public static IdentityReviewMemberDto from(Member member) {
		return new IdentityReviewMemberDto(
			member.getId(),
			member.getNickname(),
			maskName(member.getName()),
			maskPhone(member.getPhone()),
			maskEmail(member.getEmail()),
			member.getCiHash() != null,
			member.getCreateDate()
		);
	}

	// 형식이 이상한 값 하나 때문에 목록 전체가 500이 되지 않게, 가릴 수 없으면 전부 가린다
	static String maskName(String name) {
		String stripped = name.strip();
		if (stripped.isEmpty()) {
			return "*";
		}
		if (stripped.length() <= 2) {
			return stripped.substring(0, stripped.length() - 1) + "*";
		}
		return stripped.charAt(0) + "*".repeat(stripped.length() - 2) + stripped.charAt(stripped.length() - 1);
	}

	static String maskPhone(String phone) {
		String digits = phone.replaceAll("[^0-9]", "");
		if (digits.length() < 7) {
			return "***";
		}
		return digits.substring(0, 3) + "-****-" + digits.substring(digits.length() - 4);
	}

	static String maskEmail(String email) {
		int at = email.indexOf('@');
		if (at <= 0) {
			return "***";
		}
		return email.substring(0, Math.min(2, at)) + "***" + email.substring(at);
	}
}
