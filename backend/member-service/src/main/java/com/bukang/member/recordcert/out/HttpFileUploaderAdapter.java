package com.bukang.member.recordcert.out;

import java.util.Optional;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import com.bukang.common.global.rsdata.RsData;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;

/**
 * 파일 서비스 내부 API로 사진을 올린 회원을 묻는다: GET /internal/v1/file/files/{fileId}/uploader (BUK-274)
 * 404는 없거나 삭제된 파일, 그 밖의 실패는 503 (InternalLookups). 등록은 InternalClientConfig가 한다.
 * 기록증 용도가 아니거나 이미 어딘가에 연결된(UPLOADED가 아닌) 파일도 빈 값으로 돌려준다. 받으면 제출 뒤 연결만 실패하고
 * 하루 뒤 정리 배치에 원본이 지워져 판매자가 볼 수 없다
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
		return InternalLookups.get(restClient, PATH, fileId, RESPONSE_TYPE)
			.flatMap(Body::submittableUploader);
	}

	// file FileUploaderDto
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Body(Long memberId, String fileType, String status) {
		// 회원 ID가 없으면 모르는 것(503)이라 용도 · 상태보다 먼저 본다
		Optional<Long> submittableUploader() {
			long uploaderId = uploaderId();
			boolean submittable = "RECORD_CERT".equals(fileType) && "UPLOADED".equals(status);
			return submittable ? Optional.of(uploaderId) : Optional.empty();
		}

		Long uploaderId() {
			if (memberId == null) {
				throw InternalLookups.unavailable();
			}
			return memberId;
		}
	}
}
