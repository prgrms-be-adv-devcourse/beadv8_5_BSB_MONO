package com.bukang.member.out.kakao;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.bukang.member.domain.exception.KakaoUnavailableException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 카카오 REST API로 친구 목록·메시지·토큰 갱신을 부른다. 등록은 SocialLoginConfig가 한다 (2초 제한)
 * 토큰 값과 응답 본문은 로그에 남기지 않는다. 남기는 것은 카카오 오류 코드뿐이다
 */
@Slf4j
@RequiredArgsConstructor
public class RestKakaoTalkClient implements KakaoTalkClient {
	static final String FRIENDS_PATH = "/v1/api/talk/friends?limit=100";
	static final String SEND_PATH = "/v1/api/talk/friends/message/default/send";
	static final String TOKEN_PATH = "/oauth/token";

	// https://kapi.kakao.com
	private final RestClient kapi;
	// https://kauth.kakao.com
	private final RestClient kauth;
	private final String clientId;
	private final String clientSecret;

	@Override
	public KakaoRefreshedToken refresh(String refreshToken) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "refresh_token");
		form.add("client_id", clientId);
		form.add("client_secret", clientSecret);
		form.add("refresh_token", refreshToken);
		Map<?, ?> body;
		try {
			body = kauth.post().uri(TOKEN_PATH)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(Map.class);
		} catch (RestClientResponseException exception) {
			// invalid_grant만 리프레시 토큰 문제(지났거나 폐기됨)라서 다시 동의하게 한다
			// 앱 키 오류·요청 과다 같은 다른 거절은 회원 토큰을 지우지 않도록 카카오 장애로 본다
			if (exception.getStatusCode().is4xxClientError() && "invalid_grant".equals(errorField(exception))) {
				log.info("카카오 토큰 갱신이 거절됐습니다. status={}", exception.getStatusCode().value());
				throw new KakaoApiException(KakaoApiException.INVALID_TOKEN);
			}
			throw unavailable(exception);
		} catch (RestClientException exception) {
			throw unavailable(exception);
		}
		if (body == null || !(body.get("access_token") instanceof String accessToken)
			|| !(body.get("expires_in") instanceof Number expiresIn)) {
			throw new KakaoUnavailableException();
		}
		String newRefreshToken = body.get("refresh_token") instanceof String text ? text : null;
		return new KakaoRefreshedToken(accessToken, expiresIn.longValue(), newRefreshToken);
	}

	@Override
	public List<KakaoFriend> friends(String accessToken) {
		Map<?, ?> body = call(() -> kapi.get().uri(FRIENDS_PATH)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
			.retrieve()
			.body(Map.class));
		if (body == null || !(body.get("elements") instanceof List<?> elements)) {
			return List.of();
		}
		return elements.stream()
			.filter(Map.class::isInstance)
			.map(Map.class::cast)
			.filter(element -> element.get("id") != null && element.get("uuid") instanceof String)
			.map(element -> new KakaoFriend(String.valueOf(element.get("id")), (String)element.get("uuid"),
				element.get("profile_nickname") instanceof String text ? text : null,
				element.get("profile_thumbnail_image") instanceof String text ? text : null))
			.toList();
	}

	@Override
	public void send(String accessToken, String receiverUuid, String templateObjectJson) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		// uuid는 요청 DTO가 영문·숫자·기호 일부만 받으므로 따옴표를 붙여도 JSON이 깨지지 않는다
		form.add("receiver_uuids", "[\"" + receiverUuid + "\"]");
		form.add("template_object", templateObjectJson);
		Map<?, ?> body = call(() -> kapi.post().uri(SEND_PATH)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.retrieve()
			.body(Map.class));
		// 200이어도 받는 사람이 성공 목록에 없으면 실패다. failure_info의 오류 코드로 알린다
		if (body == null || !(body.get("successful_receiver_uuids") instanceof List<?> succeeded)
			|| !succeeded.contains(receiverUuid)) {
			throw new KakaoApiException(failureCode(body));
		}
	}

	private static int failureCode(Map<?, ?> body) {
		if (body != null && body.get("failure_info") instanceof List<?> failures && !failures.isEmpty()
			&& failures.get(0) instanceof Map<?, ?> failure && failure.get("code") instanceof Number code) {
			return code.intValue();
		}
		return KakaoApiException.UNKNOWN;
	}

	private static String errorField(RestClientResponseException exception) {
		try {
			Map<?, ?> body = exception.getResponseBodyAs(Map.class);
			return body != null && body.get("error") instanceof String error ? error : null;
		} catch (RuntimeException notJson) {
			return null;
		}
	}

	// 받는 사람이 한 명이면 카카오는 실패를 failure_info 없이 오류 코드로 답한다
	private <T> T call(Supplier<T> request) {
		try {
			return request.get();
		} catch (RestClientResponseException exception) {
			if (exception.getStatusCode().is5xxServerError()) {
				throw unavailable(exception);
			}
			Integer code = errorCode(exception);
			if (code == null) {
				throw unavailable(exception);
			}
			log.info("카카오 API가 오류 코드로 답했습니다. code={}", code);
			throw new KakaoApiException(code);
		} catch (RestClientException exception) {
			throw unavailable(exception);
		}
	}

	private static Integer errorCode(RestClientResponseException exception) {
		try {
			Map<?, ?> body = exception.getResponseBodyAs(Map.class);
			return body != null && body.get("code") instanceof Number number ? number.intValue() : null;
		} catch (RuntimeException notJson) {
			return null;
		}
	}

	private static KakaoUnavailableException unavailable(RestClientException exception) {
		// 예외 메시지에 응답 본문이 들어갈 수 있어 종류만 남긴다
		log.warn("카카오 API를 부르지 못했습니다. cause={}", exception.getClass().getSimpleName());
		return new KakaoUnavailableException();
	}
}
