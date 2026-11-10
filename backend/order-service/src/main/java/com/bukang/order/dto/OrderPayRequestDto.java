package com.bukang.order.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "예치금 결제 요청. 본문이 없으면 키트 옵션 없이 결제한다")
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class OrderPayRequestDto {
	@ArraySchema(arraySchema = @Schema(description = "고른 키트 옵션 ID (상품 GET /api/v1/product/races/{raceId}/kit-options의 "
		+ "kitOptionId). 대회에 옵션이 있으면 이름마다 하나씩 골라야 하고, 옵션 없는 대회는 비운다"),
		schema = @Schema(example = "11"))
	@Size(max = 20, message = "키트 옵션은 20개까지 고를 수 있습니다.")
	private List<@NotNull(message = "키트 옵션 ID가 비어 있습니다.") @Positive(message = "키트 옵션 ID가 올바르지 않습니다.") Long>
		kitOptionIds;
}
