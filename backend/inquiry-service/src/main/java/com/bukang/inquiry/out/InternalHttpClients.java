package com.bukang.inquiry.out;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.inquiry.domain.exception.InquiryExternalServiceUnavailableException;

import lombok.extern.slf4j.Slf4j;

/**
 * 다른 서비스의 내부 API(/internal/v1/...)를 부를 RestClient를 같은 기준으로 만든다
 * 상대가 느려도 요청이 오래 붙잡히지 않게 연결·응답 모두 시간 제한을 둔다. 서비스끼리는 평문 HTTP/1.1로 부른다
 */
@Slf4j
public final class InternalHttpClients {
	private InternalHttpClients() {
	}

	// 상대 오류 응답(RsData)의 code가 기대한 값인지 본다. 본문이 없거나 읽지 못하면 false (경로가 바뀐 기본 404 등)
	public static boolean hasErrorCode(HttpClientErrorException exception, String code) {
		try {
			ErrorResponse body = exception.getResponseBodyAs(ErrorResponse.class);
			return body != null && code.equals(body.code());
		} catch (RuntimeException parseFailure) {
			return false;
		}
	}

	// 오류 응답에서 판정에 쓰는 값만 받는다
	record ErrorResponse(String code) {
	}

	/**
	 * 상대 서비스를 부르는 한 가지 요청의 이름표. 로그와 503 문구에 쓴다
	 *
	 * @param service 상대 서비스 이름 (예: 회원 서비스)
	 * @param request 요청 이름 (예: 관리자 여부 요청)
	 * @param notFoundCode 「없음」으로 볼 404의 오류 코드. 없으면 어떤 404도 없음으로 보지 않는다
	 * @param unavailableMessage 503 문구
	 */
	public record Endpoint(String service, String request, String notFoundCode, String unavailableMessage) {
	}

	/**
	 * GET으로 부르고 RsData의 data를 돌려준다. 404 중 notFoundCode가 맞는 것은 빈 값이다
	 * 그 밖의 실패(시간 초과 · 연결 실패 · 5xx · 코드가 다른 404 · 그 밖의 4xx · data가 없는 응답)는 모두
	 * InquiryExternalServiceUnavailableException(503)이다. 4xx는 다시 시도해도 풀리지 않아 장애와 구분해 남긴다
	 * data 자체만 검사한다. data 안쪽 필드의 null 검사는 부르는 어댑터가 해야 한다(빠뜨리면 NPE로 500이 된다)
	 */
	public static <T> Optional<T> getData(RestClient restClient, Endpoint endpoint,
		ParameterizedTypeReference<RsData<T>> type, String path, Object... uriVariables) {
		String target = Arrays.toString(uriVariables);
		RsData<T> response;
		try {
			response = restClient.get().uri(path, uriVariables).retrieve().body(type);
		} catch (HttpClientErrorException.NotFound exception) {
			if (endpoint.notFoundCode() != null && hasErrorCode(exception, endpoint.notFoundCode())) {
				return Optional.empty();
			}
			// 경로가 바뀐 기본 404를 「없음」으로 보면 모든 요청이 조용히 없음이 되므로 503으로 본다
			log.error("{}가 {}에 대상 없음이 아닌 404로 답했습니다. 계약(경로)을 확인해야 합니다. target={}", endpoint.service(),
				endpoint.request(), target, exception);
			throw new InquiryExternalServiceUnavailableException(endpoint.unavailableMessage());
		} catch (HttpClientErrorException exception) {
			log.error("{}가 {}을 거절했습니다. 계약(경로)을 확인해야 합니다. status={}, target={}", endpoint.service(),
				endpoint.request(), exception.getStatusCode(), target, exception);
			throw new InquiryExternalServiceUnavailableException(endpoint.unavailableMessage());
		} catch (RestClientException exception) {
			log.error("{}에 {}을 보내지 못했습니다. target={}", endpoint.service(), endpoint.request(), target, exception);
			throw new InquiryExternalServiceUnavailableException(endpoint.unavailableMessage());
		}
		if (response == null || response.data() == null) {
			log.error("{}의 {} 응답에 값이 없습니다. target={}", endpoint.service(), endpoint.request(), target);
			throw new InquiryExternalServiceUnavailableException(endpoint.unavailableMessage());
		}
		return Optional.of(response.data());
	}

	public static RestClient create(String baseUrl, Duration timeout) {
		HttpClient httpClient = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(timeout)
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(timeout);
		return RestClient.builder()
			.baseUrl(baseUrl)
			.requestFactory(requestFactory)
			.build();
	}
}
