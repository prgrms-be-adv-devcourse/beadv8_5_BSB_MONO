package com.bukang.inquiry.out.member;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.inquiry.out.InternalHttpClients;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원 서비스의 판매자 신청 내부 API를 부른다 (GET /internal/v1/member/members/{memberId}/seller → RsData.data {sellerId, status})
 * - 200: status가 APPROVED일 때만 판매자다 (승인 대기·반려, 신청이 없어 status가 null인 회원은 아니다)
 * - 404 MEMBER_NOT_FOUND: 없거나 탈퇴한 회원 → 판매자가 아니다
 * - 그 밖(시간 초과 · 연결 실패 · 5xx · 코드가 다른 404 · 그 밖의 4xx · data가 없는 응답): 판매자인지 모르므로 503으로 다시 시도하게 한다
 * 등록은 MemberClientConfig가 한다 (inquiry.clients.member.mode=http)
 */
@Slf4j
@RequiredArgsConstructor
public class HttpMemberSellerAdapter implements MemberSellerPort {
	static final String PATH = "/internal/v1/member/members/{memberId}/seller";
	static final String UNAVAILABLE_MESSAGE = "회원 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.";
	private static final ParameterizedTypeReference<RsData<SellerResponse>> RESPONSE_TYPE =
		new ParameterizedTypeReference<>() {
		};

	private static final InternalHttpClients.Endpoint ENDPOINT = new InternalHttpClients.Endpoint("회원 서비스",
		"판매자 확인 요청", HttpMemberAdminAdapter.MEMBER_NOT_FOUND_CODE, UNAVAILABLE_MESSAGE);

	private final RestClient restClient;

	@Override
	public boolean isApprovedSeller(long memberId) {
		// 404 MEMBER_NOT_FOUND는 없거나 탈퇴한 회원이라 판매자가 아니다
		// 신청이 없으면 status가 null로 온다 (회원 서비스 MemberSellerDto.NONE)
		return InternalHttpClients.getData(restClient, ENDPOINT, RESPONSE_TYPE, PATH, memberId)
			.map(data -> data.status() == SellerStatus.APPROVED)
			.orElse(false);
	}

	// 회원 서비스 응답의 data (member MemberSellerDto). 빈 응답을 구분하려고 status를 null 가능으로 받는다
	@JsonIgnoreProperties(ignoreUnknown = true)
	record SellerResponse(Long sellerId, SellerStatus status) {
	}
}
