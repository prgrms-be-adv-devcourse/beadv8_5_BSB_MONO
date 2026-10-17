package com.bukang.payout.out;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.bukang.common.global.rsdata.RsData;

/**
 * 주문 서비스의 정산용 판매 내역 내부 API를 HTTP로 부르는 어댑터 (PRO-62)
 * GET {주문 서비스}/internal/v1/order/sold-entries?from=&to= → RsData.data 배열을 SoldEntry로 바꾼다
 *
 * 주문 서비스가 멈추거나 오류로 응답하면 예외를 던진다. 배치는 그날 실행을 실패로 남기고(스케줄러가 로그를 남김),
 * 다음 실행이 되돌아보기(7일)로 따라잡는다 (PRO-61). 빈 목록으로 바꾸면 그날 정산을 조용히 건너뛴 것처럼 보이므로 삼키지 않는다
 */
@Component
public class OrderSoldEntryAdapter implements SoldEntryPort {
	private static final String PATH = "/internal/v1/order/sold-entries?from={from}&to={to}";
	// 새벽 배치라 사용자를 기다리게 하지 않지만, 주문 서비스가 응답하지 않을 때 배치가 끝없이 멈추지 않게 한다
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
	// 7일치 판매 내역을 한 번에 받으므로 읽기는 넉넉히 둔다
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

	private final RestClient restClient;

	@Autowired
	public OrderSoldEntryAdapter(@Value("${payout.services.order}") String orderServiceUrl) {
		this(RestClient.builder()
			.baseUrl(orderServiceUrl)
			.requestFactory(requestFactory())
			.build());
	}

	// 테스트에서 MockRestServiceServer에 묶은 RestClient를 넣는다
	OrderSoldEntryAdapter(RestClient restClient) {
		this.restClient = restClient;
	}

	@Override
	public List<SoldEntry> findSoldEntries(LocalDate fromRaceDate, LocalDate toRaceDate) {
		RsData<List<SoldEntryResponse>> body = restClient.get()
			.uri(PATH, fromRaceDate, toRaceDate)
			.retrieve()
			.body(new ParameterizedTypeReference<>() {
			});

		if (body == null || body.data() == null) {
			throw new IllegalStateException(
				"주문 서비스의 판매 내역 응답이 비어 있습니다. raceDate=%s ~ %s".formatted(fromRaceDate, toRaceDate));
		}
		return body.data().stream()
			.map(SoldEntryResponse::toSoldEntry)
			.toList();
	}

	private static SimpleClientHttpRequestFactory requestFactory() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(CONNECT_TIMEOUT);
		factory.setReadTimeout(READ_TIMEOUT);
		return factory;
	}

	// 주문 서비스 응답(SoldEntryDto)의 모양. 포트의 SoldEntry가 응답 형식에 묶이지 않도록 따로 둔다
	// raceCourseId는 주문 서비스 응답에 아직 없다 (PRO-224에서 더한다). 없으면 NULL로 받는다
	private record SoldEntryResponse(int orderItemId, int sellerId, int raceId, Integer raceCourseId,
		LocalDate raceDate, long price) {
		SoldEntry toSoldEntry() {
			return new SoldEntry(orderItemId, sellerId, raceId, raceCourseId, raceDate, price);
		}
	}
}
