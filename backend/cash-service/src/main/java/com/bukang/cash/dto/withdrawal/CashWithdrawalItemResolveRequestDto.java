package com.bukang.cash.dto.withdrawal;

import com.bukang.cash.domain.withdrawal.WithdrawalItemResolution;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "수동 확인 필요인 환급 카드 몫 정리 요청")
@AllArgsConstructor
@Getter
public class CashWithdrawalItemResolveRequestDto {
	@Schema(description = "PG 취소 기록을 보고 내린 결론. CARD_CANCELED: 카드로 나감, NOT_CANCELED: 안 나감(예치금에 되돌림)",
		example = "CARD_CANCELED")
	@NotNull(message = "정리 결과를 작성해주세요.")
	private final WithdrawalItemResolution result;
}
