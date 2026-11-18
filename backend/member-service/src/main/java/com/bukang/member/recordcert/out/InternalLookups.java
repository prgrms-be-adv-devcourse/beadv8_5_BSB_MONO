package com.bukang.member.recordcert.out;

import java.util.Optional;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.member.recordcert.domain.exception.RecordCertCheckUnavailableException;

import lombok.extern.slf4j.Slf4j;

/**
 * 기록증 제출 때 다른 서비스 내부 API로 하나를 묻는다 (BUK-274)
 * 404는 "없음"이라 빈 값, 연결 실패 · 시간 초과 · 그 밖의 4xx · 5xx · data 없는 응답은 "모름"이라 503으로 알린다
 * 참가권 · 사진 확인은 신뢰 경계라 모르면 건너뛰지 않고 제출을 막는다 (HttpActiveTicketAdapter와 같은 방식)
 */
@Slf4j
final class InternalLookups {
	static final String UNAVAILABLE_MESSAGE = "기록증 제출을 확인하지 못했습니다. 잠시 뒤 다시 시도해주세요.";

	private InternalLookups() {
	}

	static <T> Optional<T> get(RestClient restClient, String path, long id,
		ParameterizedTypeReference<RsData<T>> type) {
		RsData<T> response;
		try {
			response = restClient.get().uri(path, id).retrieve().body(type);
		} catch (HttpClientErrorException.NotFound exception) {
			return Optional.empty();
		} catch (RestClientException exception) {
			log.warn("내부 API를 부르지 못했습니다. path={}, id={}, cause={}", path, id, exception.toString());
			throw unavailable();
		}
		if (response == null || response.data() == null) {
			log.error("내부 API 응답에 값이 없습니다. path={}, id={}, response={}", path, id, response);
			throw unavailable();
		}
		return Optional.of(response.data());
	}

	static RecordCertCheckUnavailableException unavailable() {
		return new RecordCertCheckUnavailableException(UNAVAILABLE_MESSAGE);
	}
}
