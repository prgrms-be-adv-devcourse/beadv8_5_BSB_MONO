package com.bukang.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "장바구니 담기 요청 (참가권만 담고 수량은 1로 고정)")
@AllArgsConstructor
@Getter
public class CartItemAddRequestDto {
	@Schema(description = "담을 종목(참가권) ID", example = "1")
	@NotNull(message = "담을 종목을 선택해주세요.")
	@Positive(message = "종목 ID가 올바르지 않습니다.")
	private final Long raceCourseId;
}
