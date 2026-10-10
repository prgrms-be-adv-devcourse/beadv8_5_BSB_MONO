package com.bukang.file.support;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.bukang.file.out.storage.FileStorage;
import com.bukang.file.out.storage.PresignedUpload;
import com.bukang.file.out.storage.StoredObject;

/**
 * 테스트용 메모리 저장소. 브라우저가 presigned URL로 올리는 동작은 upload()로 흉내 낸다.
 */
public class FakeFileStorage implements FileStorage {
	private static final String BASE_URL = "http://fake-s3/bukang-file/";

	private final Map<String, Stored> objects = new ConcurrentHashMap<>();
	// deleteAll에서 지우지 못한 것으로 돌려줄 키 (DeleteObjects 응답의 errors 흉내)
	private final Set<String> failingDeleteKeys = ConcurrentHashMap.newKeySet();
	// true면 deleteAll 요청 자체가 실패한다 (네트워크·권한 오류 흉내)
	private volatile boolean deleteRequestFails;

	// 브라우저의 PUT 업로드 대신 호출한다
	public void upload(String key, byte[] body, String contentType) {
		objects.put(key, new Stored(body, contentType));
	}

	public boolean exists(String key) {
		return objects.containsKey(key);
	}

	public byte[] bodyOf(String key) {
		return objects.get(key).body();
	}

	public void failOnDelete(String key) {
		failingDeleteKeys.add(key);
	}

	public void failDeleteRequest() {
		deleteRequestFails = true;
	}

	public void clear() {
		objects.clear();
		failingDeleteKeys.clear();
		deleteRequestFails = false;
	}

	@Override
	public PresignedUpload presignPut(String key, String contentType, long contentLength) {
		return new PresignedUpload(
			BASE_URL + key + "?X-Amz-Signature=fake",
			Map.of("content-type", contentType),
			Instant.parse("2026-10-09T09:10:00Z")
		);
	}

	@Override
	public Optional<StoredObject> head(String key) {
		return Optional.ofNullable(objects.get(key))
			.map(stored -> new StoredObject(stored.body().length, stored.contentType()));
	}

	@Override
	public byte[] readFirstBytes(String key, int length) {
		byte[] body = objects.get(key).body();
		return Arrays.copyOf(body, Math.min(length, body.length));
	}

	@Override
	public void copy(String sourceKey, String targetKey) {
		Stored source = objects.get(sourceKey);
		objects.put(targetKey, new Stored(source.body().clone(), source.contentType()));
	}

	@Override
	public void delete(String key) {
		objects.remove(key);
	}

	@Override
	public List<String> deleteAll(List<String> keys) {
		if (deleteRequestFails) {
			throw new IllegalStateException("S3 삭제 요청이 실패했습니다 (테스트)");
		}
		keys.stream()
			.filter(key -> !failingDeleteKeys.contains(key))
			.forEach(objects::remove);
		return keys.stream()
			.filter(failingDeleteKeys::contains)
			.toList();
	}

	@Override
	public String presignGet(String key) {
		return BASE_URL + key + "?X-Amz-Signature=fake-get";
	}

	private record Stored(byte[] body, String contentType) {
	}
}
