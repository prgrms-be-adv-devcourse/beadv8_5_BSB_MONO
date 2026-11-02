package com.bukang.cash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "참가권 취소에 따른 예치금 환불 요청")
@AllArgsConstructor
@Getter
public class CashRefundRequestDto {
	@Schema(description = "환불받을 회원 ID (지갑 주인)", example = "3")
	@NotNull(message = "회원 ID를 작성해주세요.")
	@Positive(message = "회원 ID는 0보다 커야 합니다.")
	private final Long memberId;
}
