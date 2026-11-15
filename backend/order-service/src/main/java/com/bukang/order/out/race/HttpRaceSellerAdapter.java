package com.bukang.order.out.race;

import java.util.Optional;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.out.InternalHttpClients;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 상품 서비스의 대회 판매자 내부 API를 부른다 (GET /internal/v1/product/races/{raceId}/seller, BUK-237)
 * - 200: sellerMemberId (판매자 ID가 아니라 판매자의 회원 ID)
 * - 404: 없는 대회 → 빈 값
 * - 그 밖(시간 초과 · 연결 실패 · 5xx · 그 밖의 4xx · 빈 응답): 503
 * 등록은 ProductClientConfig가 한다 (order.clients.product.mode=http)
 */
@Slf4j
@RequiredArgsConstructor
public class HttpRaceSellerAdapter implements RaceSellerPort {
	static final String PATH = "/internal/v1/product/races/{raceId}/seller";
	private static final ParameterizedTypeReference<RsData<SellerMemberBody>> RESPONSE_TYPE =
		new ParameterizedTypeReference<>() {
		};

	private final RestClient restClient;

	@Override
	public Optional<Long> findSellerMemberId(long raceId) {
		RsData<SellerMemberBody> response;
		try {
			response = restClient.get()
				.uri(PATH, raceId)
				.retrieve()
				.body(RESPONSE_TYPE);
		} catch (HttpClientErrorException exception) {
			if (exception.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
				return Optional.empty();
			}
			log.error("상품 서비스가 대회 판매자 요청을 거절했습니다. 계약(경로)을 확인해야 합니다. status={}, raceId={}, body={}",
				exception.getStatusCode(), raceId, InternalHttpClients.abbreviate(exception.getResponseBodyAsString()));
			throw new ExternalServiceUnavailableException(HttpRaceCourseAdapter.UNAVAILABLE_MESSAGE);
		} catch (RestClientException exception) {
			log.warn("상품 서비스에 대회 판매자를 묻지 못했습니다. raceId={}, cause={}", raceId, exception.toString());
			throw new ExternalServiceUnavailableException(HttpRaceCourseAdapter.UNAVAILABLE_MESSAGE);
		}
		SellerMemberBody body = response == null ? null : response.data();
		if (body == null || body.sellerMemberId() == null) {
			log.error("상품 서비스의 대회 판매자 응답에 값이 없습니다. raceId={}, response={}", raceId, response);
			throw new ExternalServiceUnavailableException(HttpRaceCourseAdapter.UNAVAILABLE_MESSAGE);
		}
		return Optional.of(body.sellerMemberId());
	}

	// 상품 서비스 응답의 data (product RaceSellerMemberDto)
	@JsonIgnoreProperties(ignoreUnknown = true)
	record SellerMemberBody(Long sellerMemberId) {
	}
}
