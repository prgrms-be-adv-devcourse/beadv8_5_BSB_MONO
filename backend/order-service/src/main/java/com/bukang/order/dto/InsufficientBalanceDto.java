package com.bukang.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 예치금 부족 응답(409 ORDER_INSUFFICIENT_BALANCE)의 data
 *
 * @param shortage 부족 금액(원). 이만큼 충전하면 같은 주문으로 다시 결제할 수 있다
 */
public record InsufficientBalanceDto(
	@Schema(description = "부족 금액(원)", example = "30000")
	long shortage
) {
}
