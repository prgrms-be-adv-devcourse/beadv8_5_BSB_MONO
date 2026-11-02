package com.bukang.order.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "장바구니 결제 요청. 장바구니에 담은 참가권(한 종목)의 참가 신청 ID를 보낸다")
@AllArgsConstructor
@Getter
public class CartCheckoutRequestDto {
	// 장바구니에는 한 종목만 담으므로 참가 신청도 1건만 받는다 (PRO-192). 요청 모양은 목록 그대로 둔다
	public static final int MAX_ITEMS = 1;

	@ArraySchema(arraySchema = @Schema(description = "결제할 참가 신청 ID 목록 (1개)"),
		schema = @Schema(example = "1"))
	@NotEmpty(message = "결제할 참가 신청을 선택해주세요.")
	@Size(max = MAX_ITEMS, message = "장바구니에는 한 종목만 담을 수 있어 참가 신청 1건만 결제할 수 있습니다.")
	private final List<
		@NotNull(message = "참가 신청 ID가 올바르지 않습니다.")
		@Positive(message = "참가 신청 ID가 올바르지 않습니다.") Long> participationIds;
}
