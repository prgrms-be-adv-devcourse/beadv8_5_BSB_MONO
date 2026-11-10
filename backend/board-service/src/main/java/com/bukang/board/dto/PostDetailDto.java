package com.bukang.board.dto;

import java.time.LocalDateTime;

import com.bukang.board.domain.BoardCategory;
import com.bukang.board.domain.BoardPost;
import com.bukang.board.domain.BoardPostStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "글 상세. 사진은 담지 않는다 (프론트가 GET /api/v1/file/files로 읽는다)")
public record PostDetailDto(
	@Schema(description = "글 ID", example = "7")
	long postId,

	@Schema(description = "말머리", example = "QUESTION")
	BoardCategory category,

	@Schema(description = "말머리 이름", example = "질문")
	String categoryLabel,

	@Schema(description = "제목", example = "한강 러닝 코스 추천해주세요")
	String title,

	@Schema(description = "본문", example = "초보자가 뛰기 좋은 코스를 알려주세요.")
	String content,

	@Schema(description = "VISIBLE(보임) 또는 HIDDEN(신고로 숨겨짐, 쓴 사람에게만 보인다)", example = "VISIBLE")
	BoardPostStatus status,

	WriterDto writer,

	@Schema(description = "내가 쓴 글인지 (로그인 안 했으면 false)", example = "false")
	boolean mine,

	@Schema(description = "고친 적이 있는지 (「수정됨」 표시)", example = "false")
	boolean edited,

	@Schema(description = "좋아요 수", example = "3")
	long likeCount,

	@Schema(description = "내가 좋아요를 눌렀는지 (로그인 안 했으면 false)", example = "false")
	boolean liked,

	@Schema(description = "쓴 시각", example = "2026-11-10T21:25:35")
	LocalDateTime createdAt
) {
	public static PostDetailDto of(BoardPost post, WriterDto writer, boolean mine, boolean liked) {
		return new PostDetailDto(post.getId(), post.getCategory(), post.getCategory().getLabel(), post.getTitle(),
			post.getContent(), post.getStatus(), writer, mine, post.isEdited(), post.getLikeCount(), liked,
			post.getCreateDate());
	}
}
