package com.bukang.file.out.storage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.bukang.file.config.S3Properties;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

@Component
@RequiredArgsConstructor
public class S3FileStorage implements FileStorage {
	// host는 브라우저가 URL에서 알아서 보내므로 응답 헤더 목록에서 뺀다
	private static final String HOST_HEADER = "host";

	private final S3Client s3Client;
	private final S3Presigner s3Presigner;
	private final S3Properties properties;

	@Override
	public PresignedUpload presignPut(String key, String contentType, long contentLength) {
		PutObjectRequest putObjectRequest = PutObjectRequest.builder()
			.bucket(properties.bucket())
			.key(key)
			.contentType(contentType)
			.contentLength(contentLength)
			.build();

		PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(request -> request
			.signatureDuration(properties.uploadUrlExpiration())
			.putObjectRequest(putObjectRequest));

		Map<String, String> headers = new LinkedHashMap<>();
		// 값이 여러 개인 헤더는 쉼표로 잇는다 (HTTP 헤더 규칙)
		presigned.signedHeaders().forEach((name, values) -> {
			if (!HOST_HEADER.equalsIgnoreCase(name)) {
				headers.put(name, String.join(",", values));
			}
		});
		return new PresignedUpload(presigned.url().toString(), headers, presigned.expiration());
	}

	@Override
	public Optional<StoredObject> head(String key) {
		try {
			HeadObjectResponse response = s3Client.headObject(request -> request
				.bucket(properties.bucket())
				.key(key));
			return Optional.of(new StoredObject(response.contentLength(), response.contentType()));
		} catch (S3Exception exception) {
			// HEAD 응답에는 본문이 없어 NoSuchKey 대신 상태 코드로 판단한다
			if (exception.statusCode() == HttpStatus.NOT_FOUND.value()) {
				return Optional.empty();
			}
			throw exception;
		}
	}

	@Override
	public byte[] readFirstBytes(String key, int length) {
		GetObjectRequest getObjectRequest = GetObjectRequest.builder()
			.bucket(properties.bucket())
			.key(key)
			.range("bytes=0-" + (length - 1))
			.build();
		try {
			return s3Client.getObjectAsBytes(getObjectRequest).asByteArray();
		} catch (S3Exception exception) {
			// 빈 객체는 범위를 만족할 수 없어 416이 온다
			if (exception.statusCode() == HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value()) {
				return new byte[0];
			}
			throw exception;
		}
	}

	@Override
	public void copy(String sourceKey, String targetKey) {
		s3Client.copyObject(request -> request
			.sourceBucket(properties.bucket())
			.sourceKey(sourceKey)
			.destinationBucket(properties.bucket())
			.destinationKey(targetKey));
	}

	@Override
	public void delete(String key) {
		s3Client.deleteObject(request -> request
			.bucket(properties.bucket())
			.key(key));
	}

	@Override
	public String presignGet(String key) {
		return s3Presigner.presignGetObject(request -> request
				.signatureDuration(properties.downloadUrlExpiration())
				.getObjectRequest(getObject -> getObject
					.bucket(properties.bucket())
					.key(key)))
			.url()
			.toString();
	}
}
