package com.bukang.member.out.product;

import java.time.YearMonth;
import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.member.crew.domain.exception.CrewPenaltyLookupFailedException;

import lombok.RequiredArgsConstructor;

/**
 * 상품 서비스 내부 API로 결제율 미달 크루를 묻는다: GET /internal/v1/product/crews/low-payment?month=2026-11
 * 연결 실패, 시간 초과, 4xx·5xx, data가 없는 응답은 모두 예외로 알려, 저장 작업이 그 달만 건너뛰게 한다
 * 등록은 InternalClientConfig가 한다
 */
@RequiredArgsConstructor
public class HttpLowPaymentCrewAdapter implements LowPaymentCrewPort {
	static final String PATH = "/internal/v1/product/crews/low-payment?month={month}";
	private static final ParameterizedTypeReference<RsData<List<LowPaymentCrew>>> RESPONSE_TYPE =
		new ParameterizedTypeReference<>() {
		};

	private final RestClient restClient;

	@Override
	public List<LowPaymentCrew> findLowPaymentCrews(YearMonth month) {
		RsData<List<LowPaymentCrew>> response;
		try {
			response = restClient.get()
				.uri(PATH, month.toString())
				.retrieve()
				.body(RESPONSE_TYPE);
		} catch (RestClientException exception) {
			throw new CrewPenaltyLookupFailedException(
				"상품 서비스에 결제율 미달 크루를 묻지 못했습니다. month=" + month + ", cause=" + exception);
		}
		if (response == null || response.data() == null) {
			throw new CrewPenaltyLookupFailedException(
				"상품 서비스의 결제율 미달 크루 응답에 값이 없습니다. month=" + month + ", response=" + response);
		}
		return response.data();
	}
}
