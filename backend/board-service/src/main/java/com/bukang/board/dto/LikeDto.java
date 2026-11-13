package com.bukang.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "좋아요를 누르거나 취소한 뒤의 상태")
public record LikeDto(
	@Schema(description = "지금 좋아요 수", example = "3")
	long likeCount,

	@Schema(description = "내가 눌렀는지", example = "true")
	boolean liked
) {
}
