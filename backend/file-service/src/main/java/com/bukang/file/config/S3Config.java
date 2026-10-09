package com.bukang.file.config;

import java.net.URI;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Slf4j
@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {
	private static final String AWS_DEFAULT_ENDPOINT = "AWS 기본 주소";

	@Bean
	public S3Client s3Client(S3Properties properties) {
		logTarget(properties);

		S3ClientBuilder builder = S3Client.builder()
			.region(Region.of(properties.region()))
			.credentialsProvider(credentialsProvider(properties))
			.forcePathStyle(properties.pathStyle());

		if (StringUtils.hasText(properties.endpoint())) {
			builder.endpointOverride(URI.create(properties.endpoint()));
		}
		return builder.build();
	}

	// presigned URL은 브라우저가 쓰므로, 공개 주소가 있으면 그 주소로 서명한다
	@Bean
	public S3Presigner s3Presigner(S3Properties properties) {
		S3Presigner.Builder builder = S3Presigner.builder()
			.region(Region.of(properties.region()))
			.credentialsProvider(credentialsProvider(properties))
			.serviceConfiguration(S3Configuration.builder()
				.pathStyleAccessEnabled(properties.pathStyle())
				.build());

		String endpoint = StringUtils.hasText(properties.publicEndpoint())
			? properties.publicEndpoint()
			: properties.endpoint();
		if (StringUtils.hasText(endpoint)) {
			builder.endpointOverride(URI.create(endpoint));
		}
		return builder.build();
	}

	// 실행할 때 어느 S3에 붙는지 남긴다. .env를 일부만 채우면 실제 버킷 이름이 로컬 S3Mock 주소로 가는 일이 생긴다
	private void logTarget(S3Properties properties) {
		boolean localEndpoint = StringUtils.hasText(properties.endpoint());
		boolean configuredKey = StringUtils.hasText(properties.accessKey());
		log.info("S3 저장소: bucket={}, endpoint={}, presigned URL 주소={}, 자격 증명={}",
			properties.bucket(),
			localEndpoint ? properties.endpoint() : AWS_DEFAULT_ENDPOINT,
			StringUtils.hasText(properties.publicEndpoint()) ? properties.publicEndpoint()
				: localEndpoint ? properties.endpoint() : AWS_DEFAULT_ENDPOINT,
			configuredKey ? "설정의 액세스 키" : "SDK 기본 자격 증명 체인");

		// AWS 액세스 키 ID는 AKIA(장기 키) 또는 ASIA(임시 키)로 시작한다
		if (localEndpoint && configuredKey && properties.accessKey().matches("^(AKIA|ASIA).*")) {
			log.warn("실제 AWS 액세스 키를 로컬 엔드포인트({})로 보내고 있습니다. "
				+ "실제 S3를 쓰려면 .env에서 S3_ENDPOINT와 S3_PUBLIC_ENDPOINT를 빈 값으로 두세요.", properties.endpoint());
		}
	}

	// 로컬에서는 .env의 키를 쓴다. .env는 Spring 설정으로만 읽혀서 SDK 기본 체인이 직접 보지 못하기 때문이다
	private AwsCredentialsProvider credentialsProvider(S3Properties properties) {
		if (StringUtils.hasText(properties.accessKey()) && StringUtils.hasText(properties.secretKey())) {
			return StaticCredentialsProvider.create(
				AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
		}
		return DefaultCredentialsProvider.builder().build();
	}
}
