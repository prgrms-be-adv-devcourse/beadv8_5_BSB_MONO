package com.bukang.product.dto;

import java.util.List;

import com.bukang.common.shared.file.domain.FileType;
import com.bukang.common.shared.file.event.FileLinkRequestedEvent;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "연결할 이미지 하나 (file-service에 업로드를 마친 파일)")
@AllArgsConstructor
@Getter
public class RaceImageRequestDto {
	@Schema(description = "업로드 완료된 파일 ID", example = "1")
	@NotNull(message = "파일 ID를 작성해주세요.")
	@Positive(message = "파일 ID는 0보다 커야 합니다.")
	private final Long fileId;

	// 업로드할 때 정한 용도와 같아야 한다 (file-service가 연결할 때 확인한다)
	@Schema(description = "파일 용도 (업로드 URL 발급 때 정한 값)", example = "THUMBNAIL")
	@NotNull(message = "파일 용도를 작성해주세요.")
	private final FileType fileType;

	@Schema(description = "같은 용도 안에서의 노출 순서 (0부터)", example = "0")
	@NotNull(message = "노출 순서를 작성해주세요.")
	@PositiveOrZero(message = "노출 순서는 0 이상이어야 합니다.")
	private final Integer sortNo;

	// 파일 연결 메시지의 항목으로 바꾼다
	public static List<FileLinkRequestedEvent.Item> toItems(List<RaceImageRequestDto> images) {
		return images.stream()
			.map(image -> new FileLinkRequestedEvent.Item(image.getFileId(), image.getFileType(), image.getSortNo()))
			.toList();
	}
}
