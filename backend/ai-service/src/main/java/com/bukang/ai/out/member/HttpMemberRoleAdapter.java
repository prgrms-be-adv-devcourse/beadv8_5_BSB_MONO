package com.bukang.ai.out.member;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.ai.domain.exception.AiExternalServiceUnavailableException;
import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.seller.domain.SellerStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원 서비스의 내부 API를 부른다 (문의 서비스의 HttpMemberAdminAdapter·HttpMemberSellerAdapter와 같은 계약)
 * - GET /internal/v1/member/members/{memberId}/admin → data.admin
 * - GET /internal/v1/member/members/{memberId}/seller → data.status가 APPROVED일 때만 판매자 (신청이 없으면 null)
 * - 404 MEMBER_NOT_FOUND: 없거나 탈퇴한 회원 → 역할이 없다
 * - 그 밖(시간 초과 · 연결 실패 · 5xx · 코드가 다른 404 · 그 밖의 4xx · 값이 없는 응답): 모르므로 503
 * 등록은 MemberClientConfig가 한다 (ai.clients.member.mode=http)
 */
@Slf4j
@RequiredArgsConstructor
public class HttpMemberRoleAdapter implements MemberRolePort {
	static final String ADMIN_PATH = "/internal/v1/member/members/{memberId}/admin";
	static final String SELLER_PATH = "/internal/v1/member/members/{memberId}/seller";
	private static final String MEMBER_NOT_FOUND_CODE = "MEMBER_NOT_FOUND";
	private static final ParameterizedTypeReference<RsData<AdminResponse>> ADMIN_TYPE =
		new ParameterizedTypeReference<>() {
		};
	private static final ParameterizedTypeReference<RsData<SellerResponse>> SELLER_TYPE =
		new ParameterizedTypeReference<>() {
		};

	private final RestClient restClient;

	@Override
	public boolean isAdmin(long memberId) {
		AdminResponse data = get(ADMIN_PATH, memberId, ADMIN_TYPE);
		if (data == null) {
			return false;
		}
		if (data.admin() == null) {
			log.error("회원 서비스의 관리자 여부 응답에 값이 없습니다. memberId={}", memberId);
			throw new AiExternalServiceUnavailableException();
		}
		return data.admin();
	}

	@Override
	public boolean isApprovedSeller(long memberId) {
		SellerResponse data = get(SELLER_PATH, memberId, SELLER_TYPE);
		return data != null && data.status() == SellerStatus.APPROVED;
	}

	// 없거나 탈퇴한 회원이면 null, 알 수 없으면 503
	private <T> T get(String path, long memberId, ParameterizedTypeReference<RsData<T>> type) {
		RsData<T> response;
		try {
			response = restClient.get().uri(path, memberId).retrieve().body(type);
		} catch (HttpClientErrorException.NotFound exception) {
			if (isMemberNotFound(exception)) {
				return null;
			}
			log.error("회원 서비스가 회원 없음이 아닌 404로 답했습니다. 계약(경로)을 확인해야 합니다. path={}, memberId={}",
				path, memberId, exception);
			throw new AiExternalServiceUnavailableException();
		} catch (RestClientException exception) {
			log.error("회원 서비스에 역할을 묻지 못했습니다. path={}, memberId={}", path, memberId, exception);
			throw new AiExternalServiceUnavailableException();
		}
		if (response == null || response.data() == null) {
			log.error("회원 서비스의 역할 응답에 값이 없습니다. path={}, memberId={}", path, memberId);
			throw new AiExternalServiceUnavailableException();
		}
		return response.data();
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

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SellerResponse(SellerStatus status) {
	}
}
