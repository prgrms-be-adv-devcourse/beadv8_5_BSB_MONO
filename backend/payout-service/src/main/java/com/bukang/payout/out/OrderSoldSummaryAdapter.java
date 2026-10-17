package com.bukang.payout.out;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.bukang.common.global.rsdata.RsData;

/**
 * 주문 서비스의 1차 정산용 판매 집계 내부 API를 HTTP로 부르는 어댑터 (PRO-214)
 * GET {주문 서비스}/internal/v1/order/sold-summary?raceId= → RsData.data 배열(판매자 × 코스)을 SoldSummary로 바꾼다
 *
 * 주문 서비스의 API는 PRO-231에서 만들었다. priceSum은 판매 내역(sold-entries)의 price와 같은 결제 금액을 더한 값이다
 * 응답이 비거나 실패하면 예외를 던진다. 배치는 그 정산 내역을 실패로 남기고 되돌아보기(7일)로 따라잡는다
 */
@Component
public class OrderSoldSummaryAdapter implements SoldSummaryPort {
	private static final String PATH = "/internal/v1/order/sold-summary?raceId={raceId}";
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

	private final RestClient restClient;

	@Autowired
	public OrderSoldSummaryAdapter(@Value("${payout.services.order}") String orderServiceUrl) {
		this(RestClient.builder()
			.baseUrl(orderServiceUrl)
			.requestFactory(requestFactory())
			.build());
	}

	// 테스트에서 MockRestServiceServer에 묶은 RestClient를 넣는다
	OrderSoldSummaryAdapter(RestClient restClient) {
		this.restClient = restClient;
	}

	@Override
	public List<SoldSummary> summarizeSold(int raceId) {
		RsData<List<SoldSummaryResponse>> body = restClient.get()
			.uri(PATH, raceId)
			.retrieve()
			.body(new ParameterizedTypeReference<>() {
			});

		if (body == null || body.data() == null) {
			throw new IllegalStateException("주문 서비스의 판매 집계 응답이 비어 있습니다. raceId=%d".formatted(raceId));
		}
		return body.data().stream()
			.map(SoldSummaryResponse::toSoldSummary)
			.toList();
	}

	private static SimpleClientHttpRequestFactory requestFactory() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(CONNECT_TIMEOUT);
		factory.setReadTimeout(READ_TIMEOUT);
		return factory;
	}

	// 주문 서비스 응답(SoldSummaryDto)의 모양. 포트의 SoldSummary가 응답 형식에 묶이지 않도록 따로 둔다
	private record SoldSummaryResponse(int sellerId, int raceId, int raceCourseId, long priceSum, int ticketCount) {
		SoldSummary toSoldSummary() {
			return new SoldSummary(sellerId, raceId, raceCourseId, priceSum, ticketCount);
		}
	}
}
