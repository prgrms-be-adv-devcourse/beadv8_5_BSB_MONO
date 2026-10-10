package com.bukang.file.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "파일 연결 요청 (요청한 목록이 그 대상의 파일 전체가 된다)")
@AllArgsConstructor
@Getter
public class FileLinkRequestDto {
	@Schema(description = "대상 소유자 회원 ID (파일을 올린 회원과 같아야 한다)", example = "1")
	@NotNull(message = "소유자 회원 ID를 작성해주세요.")
	private final Integer ownerId;

	// 빈 목록이면 그 대상의 파일을 모두 뗀다
	@Schema(description = "연결할 파일 목록 (빈 목록이면 모두 뗀다)")
	@NotNull(message = "파일 목록을 작성해주세요.")
	private final List<@Valid @NotNull(message = "파일 항목을 작성해주세요.") FileLinkItemDto> files;
}
