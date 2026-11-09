package com.bukang.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

// 수정할 때는 (이름, 값)으로 기존 옵션을 찾는다. 같은 (이름, 값)이 있으면 그 옵션의 준비 수량을 바꾸고, 없으면 새 옵션이다
@Schema(description = "키트 옵션 등록·수정 요청 (옵션 하나 = 이름 + 값)")
@AllArgsConstructor
@Getter
public class KitOptionSaveRequestDto {
	@Schema(description = "옵션 이름 (50자 이하). 같은 이름끼리 묶여 구매자가 하나를 고른다", example = "T-shirt")
	@NotBlank(message = "키트 옵션 이름을 작성해주세요.")
	@Size(max = 50, message = "키트 옵션 이름은 50자 이하로 작성해주세요.")
	private final String name;

	@Schema(description = "옵션 값 (50자 이하)", example = "L")
	@NotBlank(message = "키트 옵션 값을 작성해주세요.")
	@Size(max = 50, message = "키트 옵션 값은 50자 이하로 작성해주세요.")
	private final String value;

	@Schema(description = "준비 수량 (비우면 맞춤 제작이라 품절 없음, 숫자면 준비한 브랜드 상품 수). "
		+ "판매 시작 뒤에는 늘리기만 하고, 맞춤 제작과 준비 수량 사이에서 바꿀 수 없다", example = "100", nullable = true)
	@PositiveOrZero(message = "키트 옵션 준비 수량은 0 이상이어야 합니다.")
	private final Integer stock;
}
