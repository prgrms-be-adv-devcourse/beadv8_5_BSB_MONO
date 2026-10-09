package com.bukang.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "연결할 파일 하나")
@AllArgsConstructor
@Getter
public class FileLinkItemDto {
	@Schema(description = "업로드 완료된 파일 ID", example = "1")
	@NotNull(message = "파일 ID를 작성해주세요.")
	private final Integer fileId;

	// 값의 종류(THUMBNAIL, DETAIL 등)는 대상 서비스가 정한다
	@Schema(description = "대상 안에서의 용도 (대문자·숫자·밑줄, 20자 이하)", example = "THUMBNAIL")
	@NotNull(message = "이미지 용도를 작성해주세요.")
	@Pattern(regexp = "^[A-Z][A-Z0-9_]{0,19}$", message = "이미지 용도는 대문자, 숫자, 밑줄로 20자 이하로 작성해주세요.")
	private final String imageType;

	@Schema(description = "같은 용도 안에서의 노출 순서 (0부터)", example = "0")
	@NotNull(message = "노출 순서를 작성해주세요.")
	@PositiveOrZero(message = "노출 순서는 0 이상이어야 합니다.")
	private final Integer sortNo;
}
