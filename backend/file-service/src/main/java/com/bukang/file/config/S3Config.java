package com.bukang.file.config;

import java.net.URI;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {

	@Bean
	public S3Client s3Client(S3Properties properties) {
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

	// 로컬에서는 .env의 키를 쓴다. .env는 Spring 설정으로만 읽혀서 SDK 기본 체인이 직접 보지 못하기 때문이다
	private AwsCredentialsProvider credentialsProvider(S3Properties properties) {
		if (StringUtils.hasText(properties.accessKey()) && StringUtils.hasText(properties.secretKey())) {
			return StaticCredentialsProvider.create(
				AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
		}
		return DefaultCredentialsProvider.builder().build();
	}
}
