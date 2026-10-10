package com.bukang.file.out.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.bukang.file.config.S3Properties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3FileStorage implements FileStorage {
	// host는 브라우저가 URL에서 알아서 보내므로 응답 헤더 목록에서 뺀다
	private static final String HOST_HEADER = "host";
	// S3 DeleteObjects는 요청 하나에 최대 1000개까지 지울 수 있다
	private static final int MAX_KEYS_PER_DELETE = 1000;

	private final S3Client s3Client;
	private final S3Presigner s3Presigner;
	private final S3Properties properties;

	// 업로드 URL 발급
	@Override
	public PresignedUpload presignPut(String key, String contentType, long contentLength) {
		// Put 요청 객체 생성
		PutObjectRequest putObjectRequest = PutObjectRequest.builder()
			.bucket(properties.bucket())
			.key(key)
			.contentType(contentType)
			.contentLength(contentLength)
			.build();

		// 서명 만료시간과 Put요청 객체를 더해 presigned Put요청 객체 생성
		PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(request -> request
			.signatureDuration(properties.uploadUrlExpiration())
			.putObjectRequest(putObjectRequest));

		// Headers 조합
		Map<String, String> headers = new LinkedHashMap<>();
		// 값이 여러 개인 헤더는 쉼표로 잇는다 (HTTP 헤더 규칙)
		presigned.signedHeaders().forEach((name, values) -> {
			if (!HOST_HEADER.equalsIgnoreCase(name)) {
				headers.put(name, String.join(",", values));
			}
		});
		// presignedPutUrl, headers, 만료시간을 셋팅하여 반환
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
		// S3에게 "uploads/abc.png의 내용으로 files/abc.png라는 객체를 새로 만들어라"라고 요청 (HTTP 요청이 전달됨)
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

	// 1000개씩 나눠 요청한다. 키가 없으면 요청하지 않는다 (빈 목록을 보내면 S3가 오류를 낸다)
	@Override
	public List<String> deleteAll(List<String> keys) {
		List<String> failedKeys = new ArrayList<>();
		for (int from = 0; from < keys.size(); from += MAX_KEYS_PER_DELETE) {
			List<String> chunk = keys.subList(from, Math.min(from + MAX_KEYS_PER_DELETE, keys.size()));
			failedKeys.addAll(deleteChunk(chunk));
		}
		return failedKeys;
	}

	private List<String> deleteChunk(List<String> keys) {
		List<ObjectIdentifier> objects = keys.stream()
			.map(key -> ObjectIdentifier.builder().key(key).build())
			.toList();

		// quiet 모드: 지운 객체 목록은 빼고, 지우지 못한 것만 응답에 담는다
		DeleteObjectsResponse response = s3Client.deleteObjects(request -> request
			.bucket(properties.bucket())
			.delete(delete -> delete
				.objects(objects)
				.quiet(true)));

		// 일부만 실패하면 예외가 아니라 응답의 errors에 키별로 담겨 온다
		response.errors().forEach(error ->
			log.warn("S3 객체 삭제 실패: key={}, code={}, message={}", error.key(), error.code(), error.message()));
		return response.errors().stream()
			.map(S3Error::key)
			.toList();
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
