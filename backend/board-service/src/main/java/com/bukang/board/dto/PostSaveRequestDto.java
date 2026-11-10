package com.bukang.board.dto;

import java.util.List;

import com.bukang.board.domain.BoardCategory;
import com.bukang.board.domain.BoardPost;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "글 쓰기·고치기 요청")
public record PostSaveRequestDto(
	@Schema(description = "말머리. 비우면 FREE(자유)", example = "QUESTION")
	BoardCategory category,

	@Schema(description = "제목 (50자까지)", example = "한강 러닝 코스 추천해주세요")
	@NotBlank(message = "제목을 작성해주세요.")
	@Size(max = BoardPost.TITLE_MAX, message = "제목은 50자 이하로 작성해주세요.")
	String title,

	@Schema(description = "본문 (5,000자까지)", example = "초보자가 뛰기 좋은 코스를 알려주세요.")
	@NotBlank(message = "본문을 작성해주세요.")
	@Size(max = BoardPost.CONTENT_MAX, message = "본문은 5,000자 이하로 작성해주세요.")
	String content,

	@Schema(description = "사진 파일 ID 목록 (file-service에 올린 POST_IMAGE 파일, 최대 5개, 순서대로 보여 준다). "
		+ "고치기에서 빼면 사진을 그대로 두고, 빈 목록이면 사진을 모두 뗀다", example = "[11, 12]")
	@Size(max = 5, message = "사진은 5장까지 올릴 수 있습니다.")
	List<@Positive(message = "파일 ID는 0보다 커야 합니다.") Long> fileIds
) {
	public BoardCategory categoryOrDefault() {
		return category != null ? category : BoardCategory.FREE;
	}
}
