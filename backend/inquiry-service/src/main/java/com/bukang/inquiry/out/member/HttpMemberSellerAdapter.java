package com.bukang.inquiry.out.member;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.inquiry.domain.exception.InquiryExternalServiceUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원 서비스의 판매자 신청 내부 API를 부른다 (GET /internal/v1/member/members/{memberId}/seller → RsData.data {sellerId, status})
 * - 200: status가 APPROVED일 때만 판매자다 (승인 대기·반려는 아니다)
 * - 404: 없거나 탈퇴한 회원, 판매자 신청이 없는 회원 → 판매자가 아니다
 * - 그 밖(시간 초과 · 연결 실패 · 5xx · 그 밖의 4xx · 빈 응답): 판매자인지 모르므로 503으로 다시 시도하게 한다
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

	private final RestClient restClient;

	@Override
	public boolean isApprovedSeller(long memberId) {
		RsData<SellerResponse> response;
		try {
			response = restClient.get().uri(PATH, memberId).retrieve().body(RESPONSE_TYPE);
		} catch (HttpClientErrorException.NotFound exception) {
			return false;
		} catch (HttpClientErrorException exception) {
			// 4xx는 다시 시도해도 풀리지 않는다. 경로 계약이 어긋났을 수 있어 장애와 구분해 남긴다
			log.error("회원 서비스가 판매자 확인 요청을 거절했습니다. 계약(경로)을 확인해야 합니다. status={}, memberId={}",
				exception.getStatusCode(), memberId, exception);
			throw new InquiryExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		} catch (RestClientException exception) {
			log.error("회원 서비스에 판매자 여부를 묻지 못했습니다. memberId={}", memberId, exception);
			throw new InquiryExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		if (response == null || response.data() == null || response.data().status() == null) {
			log.error("회원 서비스의 판매자 확인 응답에 값이 없습니다. memberId={}, response={}", memberId, response);
			throw new InquiryExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		return response.data().status() == SellerStatus.APPROVED;
	}

	// 회원 서비스 응답의 data (member MemberSellerDto). 빈 응답을 구분하려고 status를 null 가능으로 받는다
	@JsonIgnoreProperties(ignoreUnknown = true)
	record SellerResponse(Long sellerId, SellerStatus status) {
	}
}
