package com.bukang.board.dto;

import java.time.LocalDateTime;

import com.bukang.board.domain.BoardCategory;
import com.bukang.board.domain.BoardPost;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "목록·검색 결과의 글 한 줄")
public record PostSummaryDto(
	@Schema(description = "글 ID", example = "7")
	long postId,

	@Schema(description = "말머리", example = "QUESTION")
	BoardCategory category,

	@Schema(description = "말머리 이름", example = "질문")
	String categoryLabel,

	@Schema(description = "제목", example = "한강 러닝 코스 추천해주세요")
	String title,

	@Schema(description = "본문 앞 100자", example = "초보자가 뛰기 좋은 코스를 알려주세요.")
	String excerpt,

	WriterDto writer,

	@Schema(description = "쓴 시각", example = "2026-11-10T21:25:35")
	LocalDateTime createdAt,

	@Schema(description = "좋아요 수", example = "3")
	long likeCount,

	@Schema(description = "댓글·답글 수 (지우거나 숨긴 것은 뺀다)", example = "2")
	long commentCount,

	// shortcut: 사진은 file-service가 들고 있고 한꺼번에 묻는 내부 API가 없어 항상 null이다. 그 API가 생기면 채운다
	@Schema(description = "첫 번째 사진 주소. 사진이 없으면 null (지금은 항상 null)", example = "null")
	String thumbnailUrl
) {
	private static final int EXCERPT_LENGTH = 100;

	public static PostSummaryDto of(BoardPost post, WriterDto writer, long commentCount) {
		String content = post.getContent();
		String excerpt = content.length() <= EXCERPT_LENGTH ? content : content.substring(0, EXCERPT_LENGTH);
		return new PostSummaryDto(post.getId(), post.getCategory(), post.getCategory().getLabel(), post.getTitle(),
			excerpt, writer, post.getCreateDate(), post.getLikeCount(), commentCount, null);
	}
}
