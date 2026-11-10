package com.bukang.cash.dto.recovery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "청구용 가상계좌 발급 요청")
@AllArgsConstructor
@Getter
public class CashClaimVirtualAccountRequestDto {
	@Schema(description = "판매자가 입금할 금액 (청구 금액)", example = "400000")
	private final Long amount;
}
