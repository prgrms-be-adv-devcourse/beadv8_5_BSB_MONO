package com.bukang.inquiry.out;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * 다른 서비스의 내부 API(/internal/v1/...)를 부를 RestClient를 같은 기준으로 만든다
 * 상대가 느려도 요청이 오래 붙잡히지 않게 연결·응답 모두 시간 제한을 둔다. 서비스끼리는 평문 HTTP/1.1로 부른다
 */
public final class InternalHttpClients {
	private InternalHttpClients() {
	}

	// 상대 오류 본문을 로그에 남길 최대 길이. dev 오류 응답은 스택 트레이스를 통째로 담아 한 줄이 수십 줄이 된다
	public static final int LOGGED_BODY_LIMIT = 300;

	public static String abbreviate(String body) {
		if (body == null || body.length() <= LOGGED_BODY_LIMIT) {
			return body;
		}
		return body.substring(0, LOGGED_BODY_LIMIT) + "…(" + body.length() + "자)";
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
