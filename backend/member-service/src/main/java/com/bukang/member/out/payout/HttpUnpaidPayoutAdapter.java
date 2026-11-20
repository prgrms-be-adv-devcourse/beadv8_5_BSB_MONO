package com.bukang.member.out.payout;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.member.domain.exception.WithdrawCheckFailedException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 정산 서비스 내부 API로 지급되지 않은 정산금이 있는지 묻는다: GET /internal/v1/payout/sellers/{sellerId}/unpaid → {hasUnpaid}
 * 연결 실패, 시간 초과, 4xx·5xx, 값 없는 응답은 모두 "모름"이라 503으로 알린다 (막는 쪽으로 실패)
 * 등록은 InternalClientConfig가 한다
 */
@Slf4j
@RequiredArgsConstructor
public class HttpUnpaidPayoutAdapter implements UnpaidPayoutPort {
	static final String PATH = "/internal/v1/payout/sellers/{sellerId}/unpaid";
	public static final String UNAVAILABLE_MESSAGE = "정산금을 확인하지 못했습니다. 잠시 뒤 다시 시도해주세요.";
	private static final ParameterizedTypeReference<RsData<Unpaid>> RESPONSE_TYPE =
		new ParameterizedTypeReference<>() {
		};

	private final RestClient restClient;

	@Override
	public boolean hasUnpaidPayout(long sellerId) {
		RsData<Unpaid> response;
		try {
			response = restClient.get()
				.uri(PATH, sellerId)
				.retrieve()
				.body(RESPONSE_TYPE);
		} catch (RestClientException exception) {
			log.warn("정산 서비스에 지급 안 된 정산금을 묻지 못했습니다. sellerId={}, cause={}", sellerId, exception.toString());
			throw new WithdrawCheckFailedException(UNAVAILABLE_MESSAGE);
		}
		if (response == null || response.data() == null || response.data().hasUnpaid() == null) {
			log.error("정산 서비스의 지급 안 된 정산금 응답에 값이 없습니다. sellerId={}, response={}", sellerId, response);
			throw new WithdrawCheckFailedException(UNAVAILABLE_MESSAGE);
		}
		return response.data().hasUnpaid();
	}

	// 정산 서비스 응답의 data. 판정에 쓰는 값만 받는다
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Unpaid(Boolean hasUnpaid) {
	}
}
