package com.bukang.cash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "충전 승인 확정 요청")
public class CashChargeConfirmRequestDto {
	@Schema(description = "Toss 결제 키", example = "5EnNZRJGvaBd3GirPNB6s")
	@NotBlank(message = "paymentKey를 작성해주세요.")
	@Size(max = 200, message = "paymentKey는 200자 이하여야 합니다.")
	private final String paymentKey;

	@Schema(description = "충전 요청 고유 ID (멱등키)", example = "charge-1697000000000")
	@NotBlank(message = "orderId를 작성해주세요.")
	@Size(max = 64, message = "orderId는 64자 이하여야 합니다.")
	private final String orderId;

	@Schema(description = "충전 요청 금액 (최소 10,000원)", example = "10000")
	@NotNull(message = "금액을 작성해주세요.")
	@Positive(message = "금액은 0보다 커야 합니다.")
	private final Long amount;
}
