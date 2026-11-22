package com.bukang.member.recordcert.dto;

import io.swagger.v3.oas.annotations.media.Schema;

// 판매자의 심사 필요 건수 (BUK-276). 대시보드 "처리할 일" 카드와 AI 브리핑이 부른다
public record RecordCertReviewCountDto(
	@Schema(description = "내 대회의 심사 필요 제출 수", example = "12")
	long waitingCount
) {
}
