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
	// S3Client와 S3Presigner가 같은 자격 증명을 함께 쓴다
	// 로컬에서는 .env의 키를 쓴다. .env는 Spring 설정으로만 읽혀서 SDK 기본 체인이 직접 보지 못하기 때문이다
	@Bean
	public AwsCredentialsProvider s3Credentials(S3Properties properties) {
		if (StringUtils.hasText(properties.accessKey()) && StringUtils.hasText(properties.secretKey())) {
			return StaticCredentialsProvider.create(
				AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
		}
		return DefaultCredentialsProvider.builder().build();
	}

	// S3에 올라간 파일 확인 . 복사 . 삭제
	@Bean
	public S3Client s3Client(S3Properties properties, AwsCredentialsProvider s3Credentials) {
		logTarget(properties);

		S3ClientBuilder builder = S3Client.builder()
			.region(Region.of(properties.region()))
			.credentialsProvider(s3Credentials)
			.forcePathStyle(properties.pathStyle()); // // S3Mock Bean 처리

		if (StringUtils.hasText(properties.endpoint())) {
			builder.endpointOverride(URI.create(properties.endpoint()));
		}
		return builder.build();
	}

	// 브라우저에 접근 URL을 생성해줌 (파일 올리기 (PUT), 파일 보기(GET))
	@Bean
	public S3Presigner s3Presigner(S3Properties properties, AwsCredentialsProvider s3Credentials) {
		S3Presigner.Builder builder = S3Presigner.builder()
			.region(Region.of(properties.region()))
			.credentialsProvider(s3Credentials)
			.serviceConfiguration(S3Configuration.builder() // S3Mock Bean 처리
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

	// 실행할 때 어느 S3에 어떤 키로 붙는지 남긴다. .env를 일부만 채우면 실제 버킷 이름이 로컬 S3Mock 주소로 가는 일이 생긴다
	private void logTarget(S3Properties properties) {
		boolean localEndpoint = StringUtils.hasText(properties.endpoint());
		boolean configuredKey = StringUtils.hasText(properties.accessKey());
		String address = localEndpoint ? properties.endpoint() : awsAddress(properties);
		String credentials = configuredKey ? "설정의 액세스 키" : "SDK 기본 자격 증명 체인";
		log.info("S3 저장소: bucket={}, 주소={}, 자격 증명={}", properties.bucket(), address, credentials);

		// 브라우저가 쓰는 presigned URL 주소가 앱이 붙는 주소와 다를 때만 남긴다 (compose 앱 컨테이너 + S3Mock)
		String publicEndpoint = properties.publicEndpoint();
		if (StringUtils.hasText(publicEndpoint) && !publicEndpoint.equals(properties.endpoint())) {
			log.info("S3 presigned URL 주소={}", publicEndpoint);
		}

		// AWS 액세스 키 ID는 AKIA(장기 키) 또는 ASIA(임시 키)로 시작한다
		if (localEndpoint && configuredKey && properties.accessKey().matches("^(AKIA|ASIA).*")) {
			log.warn("실제 AWS 액세스 키를 로컬 엔드포인트({})로 보내고 있습니다. "
				+ "실제 S3를 쓰려면 .env에서 S3_ENDPOINT와 S3_PUBLIC_ENDPOINT를 빈 값으로 두세요.", properties.endpoint());
		}
	}

	// .env에 S3_ENDPOINT=처럼 빈 값을 적으면 endpoint가 빈 문자열이 되어 SDK가 리전과 버킷으로 AWS 주소를 만든다
	// (줄을 지우거나 주석으로 두면 빈 값이 아니라 application.yaml 기본값인 S3Mock 주소가 들어온다)
	// 로그에는 SDK가 만드는 주소와 같은 모양으로 보여 준다
	private String awsAddress(S3Properties properties) {
		if (properties.pathStyle()) {
			return "https://s3.%s.amazonaws.com/%s".formatted(properties.region(), properties.bucket());
		}
		return "https://%s.s3.%s.amazonaws.com".formatted(properties.bucket(), properties.region());
	}
}
