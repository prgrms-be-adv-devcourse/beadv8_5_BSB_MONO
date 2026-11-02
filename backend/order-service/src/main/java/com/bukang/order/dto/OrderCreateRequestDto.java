package com.bukang.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "바로 결제 예매 요청 ([결제하기]). 자리 확보 전(PENDING) 참가 신청으로 주문을 만든다")
@AllArgsConstructor
@Getter
public class OrderCreateRequestDto {
	@Schema(description = "결제할 참가 신청 ID", example = "1")
	@NotNull(message = "결제할 참가 신청을 선택해주세요.")
	@Positive(message = "참가 신청 ID가 올바르지 않습니다.")
	private final Long participationId;
}
