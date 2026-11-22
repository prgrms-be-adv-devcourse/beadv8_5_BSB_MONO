package com.bukang.ai.out.file;

import java.net.URI;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.bukang.common.global.rsdata.RsData;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;

/**
 * file 내부 API GET /internal/v1/file/files/{fileId}/view-url로 presigned 주소를 받아 원본을 내려받는다
 * - 404: 없거나 지운 파일 → CertImageNotFoundException (재시도하지 않음)
 * - 그 밖(시간 초과 · 5xx · 주소 없음 · S3 실패): 예외 그대로 → 부른 쪽이 재시도
 * 등록은 FileClientConfig가 한다
 */
@RequiredArgsConstructor
public class HttpCertImageAdapter implements CertImagePort {
	static final String VIEW_URL_PATH = "/internal/v1/file/files/{fileId}/view-url";
	private static final ParameterizedTypeReference<RsData<ViewUrl>> VIEW_URL_TYPE =
		new ParameterizedTypeReference<>() {
		};

	private final RestClient fileClient;
	private final RestClient downloadClient;

	@Override
	public byte[] download(long fileId) {
		RsData<ViewUrl> response;
		try {
			response = fileClient.get().uri(VIEW_URL_PATH, fileId).retrieve().body(VIEW_URL_TYPE);
		} catch (HttpClientErrorException.NotFound notFound) {
			throw new CertImageNotFoundException(fileId);
		}
		if (response == null || response.data() == null || response.data().url() == null) {
			throw new IllegalStateException("file 보기 주소 응답에 주소가 없음: fileId=" + fileId);
		}
		// presigned 주소는 이미 인코딩돼 있어 URI로 넘긴다 (문자열로 넘기면 서명이 다시 인코딩돼 깨진다)
		byte[] image = downloadClient.get().uri(URI.create(response.data().url())).retrieve().body(byte[].class);
		if (image == null) {
			throw new IllegalStateException("기록증 원본이 비어 있음: fileId=" + fileId);
		}
		return image;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ViewUrl(String url) {
	}
}
