package com.bukang.board.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.bukang.board.domain.ReportReason;
import com.bukang.board.domain.ReportTargetType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 신고 목록 (숨겨진 글·댓글과 관리자가 처리한 글·댓글)")
public record AdminReportDto(
	@Schema(description = "지금 숨겨진 글·댓글 수", example = "2")
	long hiddenCount,

	@Schema(description = "아직 숨기지도 처리하지도 않은 글·댓글에 쌓인 신고 수", example = "12")
	long pendingReportCount,

	@Schema(description = "오늘 관리자가 처리한(되살리거나 지운) 글·댓글 수", example = "3")
	long handledTodayCount,

	@Schema(description = "숨겨진 것 먼저, 그다음 최근에 처리한 순")
	List<Item> items
) {
	public record Item(
		@Schema(description = "POST 글, COMMENT 댓글", example = "POST")
		ReportTargetType targetType,

		@Schema(description = "글 ID 또는 댓글 ID", example = "7")
		long targetId,

		@Schema(description = "글쓴이 닉네임", example = "광고왕")
		String writerNickname,

		@Schema(description = "글이면 제목, 댓글이면 댓글 내용", example = "러닝화 최저가 링크 모음")
		String content,

		@Schema(description = "댓글이면 달린 글의 제목, 글이면 null", example = "null")
		String postTitle,

		@Schema(description = "받은 신고 수 (되살리기 전 신고 포함)", example = "5")
		long reportCount,

		@Schema(description = "사유별 신고 수")
		List<ReasonCount> reasons,

		@Schema(description = "HIDDEN 숨겨짐, RESTORED 되살림, DELETED 지움", example = "HIDDEN")
		Status status,

		@Schema(description = "되살리거나 지운 시각. 숨겨진 상태면 null", example = "null")
		LocalDateTime handledAt
	) {
	}

	public record ReasonCount(
		@Schema(description = "신고 사유", example = "AD")
		ReportReason reason,

		@Schema(description = "건수", example = "5")
		long count
	) {
	}

	public enum Status {
		HIDDEN,
		RESTORED,
		DELETED
	}
}
