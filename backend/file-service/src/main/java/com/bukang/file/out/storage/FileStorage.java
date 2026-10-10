package com.bukang.file.out.storage;

import java.util.List;
import java.util.Optional;

/**
 * 파일 저장소. 파일 본문은 서버를 거치지 않고 브라우저가 presigned URL로 직접 올리고 받는다.
 * 실제 구현은 S3FileStorage이고, 테스트는 메모리 가짜 구현으로 바꿔 끼운다.
 */
public interface FileStorage {
	// 브라우저가 이 URL에 PUT으로 올린다. 서명에 들어간 헤더(Content-Type 등)를 그대로 보내야 한다
	PresignedUpload presignPut(String key, String contentType, long contentLength);

	// 올라간 객체의 크기와 형식. 객체가 없으면 빈 값
	Optional<StoredObject> head(String key);

	// 형식 판별용으로 앞부분만 읽는다. 객체가 length보다 짧으면 있는 만큼만 돌려준다
	byte[] readFirstBytes(String key, int length);

	// 같은 버킷 안에서 복사한다 (Content-Type 등 메타데이터도 함께 복사된다)
	void copy(String sourceKey, String targetKey);

	void delete(String key);

	// 여러 객체를 한꺼번에 지운다 (정리 배치). 없는 키는 지운 것으로 친다
	// 지우지 못한 키를 돌려준다. 요청 자체가 실패하면(네트워크, 권한 등) 예외가 난다
	List<String> deleteAll(List<String> keys);

	// 비공개 버킷의 객체를 잠시 열어 볼 수 있는 URL
	String presignGet(String key);
}
