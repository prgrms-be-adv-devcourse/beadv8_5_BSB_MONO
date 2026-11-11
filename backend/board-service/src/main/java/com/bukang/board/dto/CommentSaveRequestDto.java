package com.bukang.board.dto;

import com.bukang.board.domain.BoardComment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "댓글 쓰기·고치기 요청")
public record CommentSaveRequestDto(
	@Schema(description = "댓글 내용 (500자까지)", example = "저는 반포 한강공원 코스를 좋아해요.")
	@NotBlank(message = "댓글 내용을 작성해주세요.")
	@Size(max = BoardComment.CONTENT_MAX, message = "댓글은 500자 이하로 작성해주세요.")
	String content,

	@Schema(description = "답글이면 부모 댓글 ID. 댓글이면 비운다 (고치기에서는 쓰지 않는다)", example = "5")
	@Positive(message = "부모 댓글 ID는 0보다 커야 합니다.")
	Long parentId
) {
}
