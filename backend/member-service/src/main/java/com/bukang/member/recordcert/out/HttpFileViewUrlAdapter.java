package com.bukang.member.recordcert.out;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.member.recordcert.domain.exception.RecordCertFileViewUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 파일 서비스 내부 API로 기록증 사진 보기 주소(30분 유효)를 받는다: GET /internal/v1/file/files/{fileId}/view-url → {url}
 * 연결 실패, 시간 초과, 4xx·5xx, 주소 없는 응답은 모두 503으로 알린다 (BUK-276)
 * 등록은 InternalClientConfig가 한다
 */
@Slf4j
@RequiredArgsConstructor
public class HttpFileViewUrlAdapter implements FileViewUrlPort {
	static final String PATH = "/internal/v1/file/files/{fileId}/view-url";
	private static final ParameterizedTypeReference<RsData<View>> RESPONSE_TYPE =
		new ParameterizedTypeReference<>() {
		};

	private final RestClient restClient;

	@Override
	public String viewUrl(long fileId) {
		RsData<View> response;
		try {
			response = restClient.get()
				.uri(PATH, fileId)
				.retrieve()
				.body(RESPONSE_TYPE);
		} catch (RestClientException exception) {
			log.warn("파일 서비스에서 기록증 사진 보기 주소를 받지 못했습니다. fileId={}, cause={}", fileId, exception.toString());
			throw new RecordCertFileViewUnavailableException();
		}
		if (response == null || response.data() == null || response.data().url() == null) {
			log.error("파일 서비스의 보기 주소 응답에 주소가 없습니다. fileId={}", fileId);
			throw new RecordCertFileViewUnavailableException();
		}
		return response.data().url();
	}

	// 파일 서비스 응답의 data (file FileDto). 주소만 받는다
	@JsonIgnoreProperties(ignoreUnknown = true)
	record View(String url) {
	}
}
