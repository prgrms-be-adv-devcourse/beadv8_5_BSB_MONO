package com.bukang.member.recordcert.out;

import java.util.Optional;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import com.bukang.common.global.rsdata.RsData;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;

/**
 * 파일 서비스 내부 API로 사진을 올린 회원을 묻는다: GET /internal/v1/file/files/{fileId}/uploader (BUK-274)
 * 404는 없거나 삭제된 파일, 그 밖의 실패는 503 (InternalLookups). 등록은 InternalClientConfig가 한다
 */
@RequiredArgsConstructor
public class HttpFileUploaderAdapter implements FileUploaderPort {
	static final String PATH = "/internal/v1/file/files/{fileId}/uploader";
	private static final ParameterizedTypeReference<RsData<Body>> RESPONSE_TYPE =
		new ParameterizedTypeReference<>() {
		};

	private final RestClient restClient;

	@Override
	public Optional<Long> findUploader(long fileId) {
		return InternalLookups.get(restClient, PATH, fileId, RESPONSE_TYPE).map(Body::uploaderId);
	}

	// file FileUploaderDto
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Body(Long memberId) {
		Long uploaderId() {
			if (memberId == null) {
				throw InternalLookups.unavailable();
			}
			return memberId;
		}
	}
}
