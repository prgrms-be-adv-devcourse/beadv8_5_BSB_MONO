package com.bukang.file.dto;

import java.time.Instant;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 업로드 URL 발급 결과. 브라우저는 uploadUrl에 headers를 그대로 붙여 PUT으로 파일을 올린 뒤 완료 API를 부른다
 */
@AllArgsConstructor
@Getter
public class FileUploadDto {
	private final int fileId;
	private final String uploadUrl;
	private final String method;
	private final Map<String, String> headers;
	private final Instant expiresAt;
}
