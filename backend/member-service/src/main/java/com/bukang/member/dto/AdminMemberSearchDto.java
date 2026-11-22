package com.bukang.member.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

// 관리자 회원 찾기 결과 한 줄 (CRU-156). 목록에 보이지 않는 개인정보(이름·휴대폰·이메일)는 담지 않는다
@Schema(description = "관리자 회원 찾기 결과 한 줄")
public record AdminMemberSearchDto(
	@Schema(description = "회원 ID", example = "2041")
	long id,
	@Schema(description = "닉네임", example = "달리는곰")
	String nickname,
	@Schema(description = "가입 시각", example = "2025-03-02T09:00:00")
	LocalDateTime joinedAt,
	@Schema(description = "예치금 잔액(원). 예치금 서비스에 묻지 못하면 null", example = "45000", nullable = true)
	Long deposit,
	@Schema(description = "끝나지 않은 참가권 수. 주문 서비스에 묻지 못하면 null", example = "2", nullable = true)
	Integer activeTickets
) {
}
