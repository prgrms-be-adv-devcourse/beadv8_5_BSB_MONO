package com.bukang.board.dto;

import com.bukang.board.domain.ReportReason;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "글·댓글 신고 요청")
public record ReportRequestDto(
	@Schema(description = "신고 사유. AD 광고, ABUSE 욕설·비방, OBSCENE 음란, OTHER 기타", example = "AD")
	@NotNull(message = "신고 사유를 선택해주세요.")
	ReportReason reason
) {
}
