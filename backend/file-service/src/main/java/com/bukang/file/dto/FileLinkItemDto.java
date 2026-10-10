package com.bukang.file.dto;

import com.bukang.common.shared.file.domain.FileType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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

	// 업로드할 때 정한 용도와 같아야 한다 (다른 칸에 잘못 넣는 것을 막는다)
	@Schema(description = "파일 용도 (업로드 URL 발급 때 정한 값)", example = "THUMBNAIL")
	@NotNull(message = "파일 용도를 작성해주세요.")
	private final FileType fileType;

	@Schema(description = "같은 용도 안에서의 노출 순서 (0부터)", example = "0")
	@NotNull(message = "노출 순서를 작성해주세요.")
	@PositiveOrZero(message = "노출 순서는 0 이상이어야 합니다.")
	private final Integer sortNo;
}
