package com.bukang.ai.dto;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 문의 답변 초안. draft·missing은 모델이 쓰고, references·asOf는 모델에 실제로 보낸 자료를 보고 코드가 붙인다
 */
public record AiInquiryDraftDto(
	@Schema(description = "답변 초안. 자료로 채우지 못한 곳은 [확인 필요: 무엇]으로 비워 둔다") String draft,
	@Schema(description = "초안을 쓸 때 모델에 준 자료 (화면의 「참고한 정보」)") List<String> references,
	@Schema(description = "[확인 필요: …]로 비워 둔 곳의 안내. 없으면 빈 목록") List<Missing> missing,
	@Schema(description = "문의·주문 정보를 조회한 시각", example = "2026-11-15T14:03:00") LocalDateTime asOf
) {
	public record Missing(
		@Schema(description = "무엇을 못 넣었는지", example = "환불 날짜를 넣지 못했어요") String title,
		@Schema(description = "왜 못 넣었는지", example = "대회 취소 환불 일정이 자료에 없어요.") String reason
	) {
	}
}
