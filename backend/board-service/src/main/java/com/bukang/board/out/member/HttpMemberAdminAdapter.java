package com.bukang.board.out.member;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.board.domain.exception.BoardExternalServiceUnavailableException;
import com.bukang.common.global.rsdata.RsData;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원 서비스의 관리자 여부 내부 API를 부른다 (GET /internal/v1/member/members/{memberId}/admin)
 * - 200: admin 값 그대로 (일반 회원이어도 200 false)
 * - 404 MEMBER_NOT_FOUND: 없거나 탈퇴한 회원이라 관리자가 아니다. 코드가 다른 404는 경로가 어긋난 것이라 503으로 본다
 * - 그 밖(시간 초과, 연결 실패, 5xx, 그 밖의 4xx, 빈 응답): 관리자인지 모르므로 503
 */
@Slf4j
@RequiredArgsConstructor
public class HttpMemberAdminAdapter implements MemberAdminPort {
	static final String PATH = "/internal/v1/member/members/{memberId}/admin";
	static final String MEMBER_NOT_FOUND_CODE = "MEMBER_NOT_FOUND";
	static final String UNAVAILABLE_MESSAGE = "회원 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.";
	private static final ParameterizedTypeReference<RsData<AdminResponse>> RESPONSE_TYPE =
		new ParameterizedTypeReference<>() {
		};

	private final RestClient restClient;

	@Override
	public boolean isAdmin(long memberId) {
		RsData<AdminResponse> response;
		try {
			response = restClient.get().uri(PATH, memberId).retrieve().body(RESPONSE_TYPE);
		} catch (HttpClientErrorException.NotFound exception) {
			if (isMemberNotFound(exception)) {
				return false;
			}
			log.error("회원 서비스가 관리자 여부 요청에 회원 없음이 아닌 404로 답했습니다. 경로를 확인해야 합니다. memberId={}", memberId, exception);
			throw new BoardExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		} catch (RestClientException exception) {
			log.error("회원 서비스에 관리자 여부를 묻지 못했습니다. memberId={}", memberId, exception);
			throw new BoardExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		if (response == null || response.data() == null || response.data().admin() == null) {
			log.error("회원 서비스의 관리자 여부 응답에 값이 없습니다. memberId={}", memberId);
			throw new BoardExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		return response.data().admin();
	}

	private static boolean isMemberNotFound(HttpClientErrorException exception) {
		try {
			ErrorResponse body = exception.getResponseBodyAs(ErrorResponse.class);
			return body != null && MEMBER_NOT_FOUND_CODE.equals(body.code());
		} catch (RuntimeException parseFailure) {
			return false;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ErrorResponse(String code) {
	}

	// 빈 응답을 구분하려고 Boolean으로 받는다
	@JsonIgnoreProperties(ignoreUnknown = true)
	record AdminResponse(Boolean admin) {
	}
}
