package com.bukang.file.dto;

import com.bukang.common.shared.file.domain.FileType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "업로드 URL 발급 요청")
@AllArgsConstructor
@Getter
public class FileUploadRequestDto {
	@Schema(description = "파일 용도. 용도마다 받을 수 있는 파일 종류와 크기가 정해져 있다", example = "THUMBNAIL")
	@NotNull(message = "파일 용도를 작성해주세요.")
	private final FileType fileType;

	@Schema(description = "원본 파일명 (255자 이하)", example = "race-thumbnail.png")
	@NotBlank(message = "파일명을 작성해주세요.")
	@Size(max = 255, message = "파일명은 255자 이하로 작성해주세요.")
	private final String fileName;

	@Schema(description = "파일 형식 (MIME 타입, 브라우저 File.type 값). 이미지 JPG·PNG·WebP, 동영상 MP4·MOV, 엑셀 XLSX·XLS",
		example = "image/png")
	@NotBlank(message = "파일 형식을 작성해주세요.")
	private final String contentType;

	@Schema(description = "파일 크기 (바이트). 이미지 10MB, 동영상 100MB, 엑셀 5MB 이하", example = "204800")
	@NotNull(message = "파일 크기를 작성해주세요.")
	@Positive(message = "파일 크기는 0보다 커야 합니다.")
	private final Long fileSize;
}
