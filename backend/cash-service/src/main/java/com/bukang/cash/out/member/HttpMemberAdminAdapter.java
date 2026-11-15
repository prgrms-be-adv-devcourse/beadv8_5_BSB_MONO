package com.bukang.cash.out.member;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.cash.domain.exception.admin.MemberServiceUnavailableException;
import com.bukang.common.global.rsdata.RsData;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원 서비스의 관리자 여부 내부 API를 부른다 (GET /internal/v1/member/members/{memberId}/admin). order-service와 같은 판정이다
 * - 200: admin 값 그대로 (일반 회원이어도 200 false)
 * - 404 MEMBER_NOT_FOUND: 없거나 탈퇴한 회원 → 관리자가 아니다 (403은 캐시가 정한다)
 *   코드가 다른 404(경로가 바뀌어 회원 서비스가 기본 404를 줌)는 계약이 어긋난 것이라 모든 관리자가 403이 되지 않게 503으로 본다
 * - 그 밖(시간 초과 · 연결 실패 · 5xx · 그 밖의 4xx · 빈 응답): 관리자인지 모르므로 503으로 다시 시도하게 한다
 * 등록은 MemberClientConfig가 한다 (cash.clients.member.mode=http)
 */
@Slf4j
@RequiredArgsConstructor
public class HttpMemberAdminAdapter implements MemberAdminPort {
	static final String PATH = "/internal/v1/member/members/{memberId}/admin";
	static final String MEMBER_NOT_FOUND_CODE = "MEMBER_NOT_FOUND";
	private static final ParameterizedTypeReference<RsData<AdminResponse>> RESPONSE_TYPE =
		new ParameterizedTypeReference<>() {
		};

	private final RestClient restClient;

	@Override
	public boolean isAdmin(long memberId) {
		RsData<AdminResponse> response;
		try {
			response = restClient.get()
				.uri(PATH, memberId)
				.retrieve()
				.body(RESPONSE_TYPE);
		} catch (HttpClientErrorException.NotFound exception) {
			if (isMemberNotFound(exception)) {
				return false;
			}
			log.error("회원 서비스가 관리자 여부 요청에 회원 없음이 아닌 404로 답했습니다. 계약(경로)을 확인해야 합니다. memberId={}",
				memberId, exception);
			throw new MemberServiceUnavailableException();
		} catch (HttpClientErrorException exception) {
			// 4xx는 다시 시도해도 풀리지 않는다. 경로 계약이 어긋났을 수 있어 장애와 구분해 남긴다
			log.error("회원 서비스가 관리자 여부 요청을 거절했습니다. 계약(경로)을 확인해야 합니다. status={}, memberId={}",
				exception.getStatusCode(), memberId, exception);
			throw new MemberServiceUnavailableException();
		} catch (RestClientException exception) {
			log.error("회원 서비스에 관리자 여부를 묻지 못했습니다. memberId={}", memberId, exception);
			throw new MemberServiceUnavailableException();
		}
		if (response == null || response.data() == null || response.data().admin() == null) {
			log.error("회원 서비스의 관리자 여부 응답에 값이 없습니다. memberId={}, response={}", memberId, response);
			throw new MemberServiceUnavailableException();
		}
		return response.data().admin();
	}

	// 회원 서비스가 회원을 찾지 못했다고 답한 404인지 (오류 응답 RsData의 code)
	private static boolean isMemberNotFound(HttpClientErrorException exception) {
		try {
			ErrorResponse body = exception.getResponseBodyAs(ErrorResponse.class);
			return body != null && MEMBER_NOT_FOUND_CODE.equals(body.code());
		} catch (RuntimeException parseFailure) {
			return false;
		}
	}

	// 회원 서비스 오류 응답에서 판정에 쓰는 값만 받는다
	@JsonIgnoreProperties(ignoreUnknown = true)
	record ErrorResponse(String code) {
	}

	// 회원 서비스 응답의 data (member MemberAdminDto). 빈 응답을 구분하려고 Boolean으로 받는다
	@JsonIgnoreProperties(ignoreUnknown = true)
	record AdminResponse(Boolean admin) {
	}
}
