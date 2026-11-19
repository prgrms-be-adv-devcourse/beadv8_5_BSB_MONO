package com.bukang.inquiry.out.race;

import java.util.Optional;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.inquiry.domain.exception.InquiryExternalServiceUnavailableException;
import com.bukang.inquiry.out.InternalHttpClients;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 상품 서비스의 대회 판매자 내부 API를 부른다 (GET /internal/v1/product/races/{raceId}/seller → RsData.data {sellerMemberId})
 * sellerMemberId는 판매자 ID가 아니라 판매자를 신청한 회원 ID다. 취소·판매 종료된 대회도 돌려준다
 * - 200: 판매자 회원 ID
 * - 404 RACE_NOT_FOUND: 없는 대회 → 빈 값 (문의를 만들 때 InquiryRaceNotFoundException으로 404가 된다)
 * - 그 밖(시간 초과 · 연결 실패 · 5xx · 코드가 다른 404 · 그 밖의 4xx · 값이 없는 응답): 503
 * 등록은 ProductClientConfig가 한다 (inquiry.clients.product.mode=http)
 */
@Slf4j
@RequiredArgsConstructor
public class HttpRaceSellerAdapter implements RaceSellerPort {
	static final String PATH = "/internal/v1/product/races/{raceId}/seller";
	static final String RACE_NOT_FOUND_CODE = "RACE_NOT_FOUND";
	static final String UNAVAILABLE_MESSAGE = "상품 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.";
	private static final ParameterizedTypeReference<RsData<SellerMemberResponse>> RESPONSE_TYPE =
		new ParameterizedTypeReference<>() {
		};

	private static final InternalHttpClients.Endpoint ENDPOINT = new InternalHttpClients.Endpoint("상품 서비스",
		"대회 판매자 요청", RACE_NOT_FOUND_CODE, UNAVAILABLE_MESSAGE);

	private final RestClient restClient;

	@Override
	public Optional<Long> findSellerMemberId(long raceId) {
		return InternalHttpClients.getData(restClient, ENDPOINT, RESPONSE_TYPE, PATH, raceId)
			.map(data -> {
				if (data.sellerMemberId() == null) {
					log.error("상품 서비스의 대회 판매자 응답에 값이 없습니다. raceId={}", raceId);
					throw new InquiryExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
				}
				return data.sellerMemberId();
			});
	}

	// 상품 서비스 응답의 data (product RaceSellerMemberDto). 빈 응답을 구분하려고 Long으로 받는다
	@JsonIgnoreProperties(ignoreUnknown = true)
	record SellerMemberResponse(Long sellerMemberId) {
	}
}
