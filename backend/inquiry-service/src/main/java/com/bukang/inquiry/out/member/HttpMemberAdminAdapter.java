package com.bukang.inquiry.out.member;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.inquiry.domain.exception.InquiryExternalServiceUnavailableException;
import com.bukang.inquiry.out.InternalHttpClients;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원 서비스의 관리자 여부 내부 API를 부른다 (GET /internal/v1/member/members/{memberId}/admin)
 * - 200: admin 값 그대로 (일반 회원이어도 200 false)
 * - 404 MEMBER_NOT_FOUND: 없거나 탈퇴한 회원 → 관리자가 아니다 (403은 문의 서비스가 정한다)
 *   코드가 다른 404(경로가 바뀌어 회원 서비스가 기본 404를 줌)는 계약이 어긋난 것이라 모든 관리자가 403이 되지 않게 503으로 본다
 * - 그 밖(시간 초과 · 연결 실패 · 5xx · 그 밖의 4xx · 빈 응답): 관리자인지 모르므로 503으로 다시 시도하게 한다
 * 등록은 MemberClientConfig가 한다 (inquiry.clients.member.mode=http)
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

	private static final InternalHttpClients.Endpoint ENDPOINT = new InternalHttpClients.Endpoint("회원 서비스",
		"관리자 여부 요청", MEMBER_NOT_FOUND_CODE, UNAVAILABLE_MESSAGE);

	private final RestClient restClient;

	@Override
	public boolean isAdmin(long memberId) {
		// 404 MEMBER_NOT_FOUND는 없거나 탈퇴한 회원이라 관리자가 아니다
		return InternalHttpClients.getData(restClient, ENDPOINT, RESPONSE_TYPE, PATH, memberId)
			.map(data -> {
				if (data.admin() == null) {
					log.error("회원 서비스의 관리자 여부 응답에 값이 없습니다. memberId={}, data={}", memberId, data);
					throw new InquiryExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
				}
				return data.admin();
			})
			.orElse(false);
	}

	// 회원 서비스 응답의 data (member MemberAdminDto). 빈 응답을 구분하려고 Boolean으로 받는다
	@JsonIgnoreProperties(ignoreUnknown = true)
	record AdminResponse(Boolean admin) {
	}
}
