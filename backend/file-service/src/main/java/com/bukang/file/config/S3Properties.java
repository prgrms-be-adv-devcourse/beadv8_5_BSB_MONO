package com.bukang.file.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * S3 접속 설정 (file.storage.s3)
 * endpoint가 비어 있으면 실제 AWS S3를, 값이 있으면 그 주소(로컬 S3Mock 등)를 쓴다.
 * accessKey가 비어 있으면 SDK 기본 자격 증명 체인(환경변수, ~/.aws, EC2 역할 등)을 쓴다.
 */
@Validated
@ConfigurationProperties(prefix = "file.storage.s3")
public record S3Properties(
	@NotBlank String bucket,
	@NotBlank String region,
	String endpoint,
	// presigned URL에 넣을 주소. 앱은 컨테이너 안 주소로 접속하지만 브라우저는 다른 주소로 접속할 때 쓴다
	String publicEndpoint,
	boolean pathStyle,
	String accessKey,
	String secretKey,
	@NotNull Duration uploadUrlExpiration,
	@NotNull Duration downloadUrlExpiration
) {
}
