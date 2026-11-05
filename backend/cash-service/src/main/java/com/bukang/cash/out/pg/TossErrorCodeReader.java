package com.bukang.cash.out.pg;

import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.extern.slf4j.Slf4j;

// Toss 오류 응답({code, message})을 읽는다. 승인·취소 실패 양쪽에서 같이 쓴다
@Slf4j
final class TossErrorCodeReader {
	private TossErrorCodeReader() {
	}

	// 본문이 JSON이 아니면(앞단 프록시의 HTML 오류 페이지 등) null을 돌려준다
	static ErrorResponse read(RestClientResponseException exception) {
		try {
			return exception.getResponseBodyAs(ErrorResponse.class);
		} catch (RuntimeException parseException) {
			log.warn("Toss 오류 응답 본문을 파싱하지 못했습니다. body={}", exception.getResponseBodyAsString());
			return null;
		}
	}

	static String readCode(RestClientResponseException exception) {
		ErrorResponse error = read(exception);
		return error == null ? null : error.code();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ErrorResponse(String code, String message) {
	}
}
