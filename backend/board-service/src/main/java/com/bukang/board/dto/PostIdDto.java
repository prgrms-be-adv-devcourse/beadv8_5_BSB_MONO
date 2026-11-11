package com.bukang.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "만든 글")
public record PostIdDto(
	@Schema(description = "글 ID", example = "7")
	long postId
) {
}
