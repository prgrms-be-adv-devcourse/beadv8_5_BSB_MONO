package com.bukang.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 사람이 검색 문서를 직접 고친 뒤 처리 실패한 문서 동기화 행을 닫을 때 남기는 메모 (BUK-162)
// 칸이 하나뿐이라 record로 받는다 (DltCompleteRequestDto와 같은 이유)
@Schema(description = "검색 문서 동기화 처리 완료 요청")
public record RaceSyncCompleteRequestDto(
	@Schema(description = "처리 완료 메모 (무엇을 했는지, 500자 이하)", example = "Kibana에서 대회 7의 종목 문서 2개를 직접 고침")
	@NotBlank(message = "처리 완료 메모를 작성해주세요.")
	@Size(max = 500, message = "처리 완료 메모는 500자 이하로 작성해주세요.")
	String memo
) {
}
