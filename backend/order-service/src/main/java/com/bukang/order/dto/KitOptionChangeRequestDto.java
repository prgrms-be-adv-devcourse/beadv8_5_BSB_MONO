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

@Schema(description = "결제한 참가권의 키트 옵션 바꾸기 요청. 결제 때처럼 바꾼 뒤의 옵션을 모두 보낸다")
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class KitOptionChangeRequestDto {
	@ArraySchema(arraySchema = @Schema(description = "바꾼 뒤의 키트 옵션 ID 전체 "
		+ "(상품 GET /api/v1/product/races/{raceId}/kit-options의 kitOptionId). "
		+ "옵션 이름마다 하나씩 골라야 하고, 옵션 없는 대회는 비운다"),
		schema = @Schema(example = "11"))
	@Size(max = 20, message = "키트 옵션은 20개까지 고를 수 있습니다.")
	private List<@NotNull(message = "키트 옵션 ID가 비어 있습니다.") @Positive(message = "키트 옵션 ID가 올바르지 않습니다.") Long>
		kitOptionIds;
}
