package com.bukang.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "만든 댓글")
public record CommentIdDto(
	@Schema(description = "댓글 ID", example = "21")
	long commentId
) {
}
