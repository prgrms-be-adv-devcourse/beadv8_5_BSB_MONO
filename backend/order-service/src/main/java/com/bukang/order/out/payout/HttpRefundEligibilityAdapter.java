package com.bukang.order.out.payout;

import java.time.LocalDate;

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
 * 정산 서비스(E)의 내부 API로 환불 가능 여부를 묻는다 (GET /internal/v1/payout/refund-eligibility?raceDate=, PRO-99)
 * 응답이 없거나 형식이 다르면 환불해도 되는지 알 수 없으므로, 취소를 진행하지 않고 503으로 다시 시도하게 한다
 * 등록은 PayoutClientConfig가 한다 (order.clients.payout.mode=http)
 */
@Slf4j
@RequiredArgsConstructor
public class HttpRefundEligibilityAdapter implements RefundEligibilityPort {
	static final String PATH = "/internal/v1/payout/refund-eligibility";
	static final String UNAVAILABLE_MESSAGE = "정산 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.";
	private static final ParameterizedTypeReference<RsData<Eligibility>> RESPONSE_TYPE =
		new ParameterizedTypeReference<>() {
		};

	private final RestClient restClient;

	@Override
	public boolean isRefundable(LocalDate raceDate) {
		RsData<Eligibility> response;
		try {
			response = restClient.get()
				.uri(uriBuilder -> uriBuilder.path(PATH).queryParam("raceDate", raceDate).build())
				.retrieve()
				.body(RESPONSE_TYPE);
		} catch (HttpClientErrorException exception) {
			// 4xx는 다시 시도해도 풀리지 않는다. 경로·파라미터 계약이 어긋났을 수 있어 장애와 구분해 남긴다
			log.error("정산 서비스가 환불 가능 여부 요청을 거절했습니다. 계약(경로·파라미터)을 확인해야 합니다. status={}, raceDate={}",
				exception.getStatusCode(), raceDate, exception);
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		} catch (RestClientException exception) {
			log.error("정산 서비스에 환불 가능 여부를 묻지 못했습니다. raceDate={}", raceDate, exception);
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		if (response == null || response.data() == null || response.data().refundable() == null) {
			log.error("정산 서비스의 환불 가능 여부 응답에 값이 없습니다. raceDate={}", raceDate);
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
		return response.data().refundable();
	}

	// 정산 서비스 응답의 data (payout RefundEligibilityDto). 판정에 쓰는 값만 받는다
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Eligibility(Boolean refundable) {
	}
}
