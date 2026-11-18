package com.bukang.member.recordcert.out;

import org.springframework.web.client.RestClient;

import lombok.RequiredArgsConstructor;

/**
 * 파일 서비스 내부 API로 기록증 사진을 지운다: DELETE /internal/v1/file/files/{fileId} (BUK-278)
 * 연결 실패, 시간 초과, 4xx·5xx는 RestClientException으로 그대로 던진다. 부른 쪽이 다음 날 다시 부른다
 * 등록은 InternalClientConfig가 한다
 */
@RequiredArgsConstructor
public class HttpFileDeleteAdapter implements FileDeletePort {
	static final String PATH = "/internal/v1/file/files/{fileId}";

	private final RestClient restClient;

	@Override
	public void delete(long fileId) {
		restClient.delete()
			.uri(PATH, fileId)
			.retrieve()
			.toBodilessEntity();
	}
}
