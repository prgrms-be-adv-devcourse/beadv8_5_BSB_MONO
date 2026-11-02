package com.bukang.cash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "주문 결제 요청")
@AllArgsConstructor
@Getter
public class CashPaymentRequestDto {
	@Schema(description = "결제할 회원 ID (지갑 주인)", example = "3")
	@NotNull(message = "회원 ID를 작성해주세요.")
	@Positive(message = "회원 ID는 0보다 커야 합니다.")
	private final Long memberId;

	@Schema(description = "결제 금액", example = "10000")
	@NotNull(message = "결제 금액을 작성해주세요.")
	@Positive(message = "결제 금액은 0보다 커야 합니다.")
	private final Long amount;
}
