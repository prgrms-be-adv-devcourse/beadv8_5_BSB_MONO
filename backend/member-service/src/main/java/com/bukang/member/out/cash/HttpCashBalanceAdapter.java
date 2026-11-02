package com.bukang.member.out.cash;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.member.domain.exception.WithdrawCheckFailedException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 예치금 서비스 내부 API로 회원 잔액을 묻는다: GET /internal/v1/cash/wallets/{memberId}/balance → {balance}
 * 연결 실패, 시간 초과, 4xx·5xx, 값 없는 응답은 모두 "모름"이라 503으로 알린다 (막는 쪽으로 실패, BE-022 결정 2)
 * 등록은 InternalClientConfig가 한다
 */
@Slf4j
@RequiredArgsConstructor
public class HttpCashBalanceAdapter implements CashBalancePort {
	static final String PATH = "/internal/v1/cash/wallets/{memberId}/balance";
	public static final String UNAVAILABLE_MESSAGE = "예치금을 확인하지 못했습니다. 잠시 뒤 다시 시도해주세요.";
	private static final ParameterizedTypeReference<RsData<Balance>> RESPONSE_TYPE =
		new ParameterizedTypeReference<>() {
		};

	private final RestClient restClient;

	@Override
	public long findBalance(long memberId) {
		RsData<Balance> response;
		try {
			response = restClient.get()
				.uri(PATH, memberId)
				.retrieve()
				.body(RESPONSE_TYPE);
		} catch (RestClientException exception) {
			log.warn("예치금 서비스에 잔액을 묻지 못했습니다. memberId={}, cause={}", memberId, exception.toString());
			throw new WithdrawCheckFailedException(UNAVAILABLE_MESSAGE);
		}
		if (response == null || response.data() == null || response.data().balance() == null) {
			log.error("예치금 서비스의 잔액 응답에 값이 없습니다. memberId={}, response={}", memberId, response);
			throw new WithdrawCheckFailedException(UNAVAILABLE_MESSAGE);
		}
		return response.data().balance();
	}

	// 예치금 서비스 응답의 data (cash CashBalanceDto). 판정에 쓰는 값만 받는다
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Balance(Long balance) {
	}
}
