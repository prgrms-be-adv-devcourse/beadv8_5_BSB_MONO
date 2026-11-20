package com.bukang.order.out.seller;

import java.util.Optional;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원 서비스의 판매자 신청 내부 API를 부른다 (GET /internal/v1/member/members/{memberId}/seller, BUK-237)
 * - 200: 승인(APPROVED)이면 판매자 ID, 승인 대기·반려·신청 없음(sellerId·status 모두 null)이면 빈 값 → 주문이 403
 * - 그 밖(404 · 시간 초과 · 연결 실패 · 5xx · 그 밖의 4xx · 빈 응답): 판매자인지 모르므로 503으로 다시 시도하게 한다
 *   게이트웨이를 지난 로그인 회원이라 404는 탈퇴 직후이거나 경로가 어긋난 것이다. 403으로 숨기지 않고 장애로 드러낸다
 * 등록은 SellerClientConfig가 한다 (order.clients.seller.mode=http)
 */
@Slf4j
@RequiredArgsConstructor
public class HttpSellerLookupAdapter implements SellerLookupPort {
	static final String PATH = "/internal/v1/member/members/{memberId}/seller";
	static final String APPROVED = "APPROVED";
	static final String UNAVAILABLE_MESSAGE = "회원 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.";
	private static final ParameterizedTypeReference<RsData<SellerResponse>> RESPONSE_TYPE =
		new ParameterizedTypeReference<>() {
		};

	private final RestClient restClient;

	@Override
	public Optional<Long> findApprovedSellerId(long memberId) {
		RsData<SellerResponse> response;
		try {
			response = restClient.get()
				.uri(PATH, memberId)
				.retrieve()
				.body(RESPONSE_TYPE);
		} catch (HttpClientErrorException exception) {
			// 4xx는 다시 시도해도 풀리지 않는다. 경로 계약이 어긋났을 수 있어 장애와 구분해 남긴다
			log.error("회원 서비스가 판매자 조회를 거절했습니다. status={}, memberId={}", exception.getStatusCode(),
				memberId);
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		} catch (RestClientException exception) {
			log.warn("회원 서비스에 판매자를 묻지 못했습니다. memberId={}, cause={}", memberId, exception.toString());
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		SellerResponse body = response == null ? null : response.data();
		if (body == null) {
			log.error("회원 서비스의 판매자 조회 응답이 비어 있습니다. memberId={}", memberId);
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		if (!APPROVED.equals(body.status())) {
			return Optional.empty();
		}
		if (body.sellerId() == null) {
			log.error("회원 서비스가 승인된 판매자에 판매자 ID 없이 답했습니다. memberId={}", memberId);
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		return Optional.of(body.sellerId());
	}

	// 회원 서비스 응답의 data (member MemberSellerDto). 상태는 common SellerStatus 이름 그대로 문자열로 받는다
	@JsonIgnoreProperties(ignoreUnknown = true)
	record SellerResponse(Long sellerId, String status) {
	}
}
