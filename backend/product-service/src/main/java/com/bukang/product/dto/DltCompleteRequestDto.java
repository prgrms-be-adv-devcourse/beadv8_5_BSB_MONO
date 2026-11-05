package com.bukang.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 사람이 원인을 고친 뒤 DLT 실패 행을 처리 완료로 닫을 때 남기는 메모
// 칸이 하나뿐이라 record로 받는다 (Lombok 생성자 하나짜리 클래스는 Jackson이 본문 전체를 그 값으로 읽으려 할 수 있다)
@Schema(description = "DLT 메시지 처리 완료 요청")
public record DltCompleteRequestDto(
	@Schema(description = "처리 완료 메모 (무엇을 했는지, 500자 이하)", example = "DB에서 자리 수를 직접 고쳐 다시 보내지 않고 닫음")
	@NotBlank(message = "처리 완료 메모를 작성해주세요.")
	@Size(max = 500, message = "처리 완료 메모는 500자 이하로 작성해주세요.")
	String memo
) {
}
