package com.bukang.inquiry.out.race;

import java.util.Optional;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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

	private final RestClient restClient;

	@Override
	public Optional<Long> findSellerMemberId(long raceId) {
		RsData<SellerMemberResponse> response;
		try {
			response = restClient.get().uri(PATH, raceId).retrieve().body(RESPONSE_TYPE);
		} catch (HttpClientErrorException.NotFound exception) {
			if (InternalHttpClients.hasErrorCode(exception, RACE_NOT_FOUND_CODE)) {
				return Optional.empty();
			}
			log.error("상품 서비스가 대회 판매자 요청에 대회 없음이 아닌 404로 답했습니다. 계약(경로)을 확인해야 합니다. raceId={}, body={}",
				raceId, InternalHttpClients.abbreviate(exception.getResponseBodyAsString()), exception);
			throw new InquiryExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		} catch (HttpClientErrorException exception) {
			log.error("상품 서비스가 대회 판매자 요청을 거절했습니다. 계약(경로)을 확인해야 합니다. status={}, raceId={}",
				exception.getStatusCode(), raceId, exception);
			throw new InquiryExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		} catch (RestClientException exception) {
			log.error("상품 서비스에 대회 판매자를 묻지 못했습니다. raceId={}", raceId, exception);
			throw new InquiryExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		if (response == null || response.data() == null || response.data().sellerMemberId() == null) {
			log.error("상품 서비스의 대회 판매자 응답에 값이 없습니다. raceId={}, response={}", raceId, response);
			throw new InquiryExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		return Optional.of(response.data().sellerMemberId());
	}

	// 상품 서비스 응답의 data (product RaceSellerMemberDto). 빈 응답을 구분하려고 Long으로 받는다
	@JsonIgnoreProperties(ignoreUnknown = true)
	record SellerMemberResponse(Long sellerMemberId) {
	}
}
