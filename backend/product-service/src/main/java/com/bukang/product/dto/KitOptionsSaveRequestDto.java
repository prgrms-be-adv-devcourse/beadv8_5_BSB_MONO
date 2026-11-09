package com.bukang.product.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

// 대회의 키트 옵션 전체. 등록할 때는 1개 이상, 수정할 때는 바꾼 뒤의 목록 전체를 보낸다 (BUK-137)
@Schema(description = "키트 옵션 등록·수정 요청 (대회의 키트 옵션 전체)")
@AllArgsConstructor
@Getter
public class KitOptionsSaveRequestDto {
	@Schema(description = "키트 옵션 목록. 등록할 때는 1개 이상이고, 수정할 때는 바꾼 뒤의 목록 전체를 보낸다 "
		+ "(빈 목록이면 옵션을 모두 뺀다. 판매 시작 뒤에는 뺄 수 없다)")
	@NotNull(message = "키트 옵션 목록을 작성해주세요.")
	private final List<@Valid @NotNull(message = "키트 옵션 항목을 작성해주세요.") KitOptionSaveRequestDto> kitOptions;
}
