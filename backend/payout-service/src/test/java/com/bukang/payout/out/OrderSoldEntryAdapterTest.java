package com.bukang.payout.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 주문 서비스의 판매 내역 내부 API(GET /internal/v1/order/sold-entries)를 부르는 어댑터 (PRO-62)
 * 실제 주문 서비스 대신 MockRestServiceServer로 응답을 흉내 낸다
 */
class OrderSoldEntryAdapterTest {
	private static final String BASE_URL = "http://order-service";
	private static final String URL =
		BASE_URL + "/internal/v1/order/sold-entries?from=2026-09-22&to=2026-09-28";

	private MockRestServiceServer server;
	private OrderSoldEntryAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new OrderSoldEntryAdapter(builder.build());
	}

	@Test
	@DisplayName("기간을 쿼리로 보내고, RsData.data 배열을 SoldEntry로 바꾼다")
	void mapsSoldEntries() {
		server.expect(requestTo(URL))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("""
				{
					"status": 200,
					"message": "판매 내역을 조회했습니다.",
					"data": [
						{ "orderItemId": 1, "sellerId": 3, "raceId": 5, "raceDate": "2026-09-22", "price": 60000 },
						{ "orderItemId": 4, "sellerId": 3, "raceId": 7, "raceDate": "2026-09-28", "price": 55000 }
					]
				}
				""", MediaType.APPLICATION_JSON));

		List<SoldEntry> entries = adapter.findSoldEntries(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 28));

		assertThat(entries).containsExactly(
			new SoldEntry(1, 3, 5, null, LocalDate.of(2026, 9, 22), 60_000L),
			new SoldEntry(4, 3, 7, null, LocalDate.of(2026, 9, 28), 55_000L)
		);
		server.verify();
	}

	@Test
	@DisplayName("판매 내역이 없으면 빈 목록이다")
	void emptyList() {
		server.expect(requestTo(URL))
			.andRespond(withSuccess("""
				{ "status": 200, "message": "판매 내역을 조회했습니다.", "data": [] }
				""", MediaType.APPLICATION_JSON));

		assertThat(adapter.findSoldEntries(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 28))).isEmpty();
	}

	@Test
	@DisplayName("주문 서비스가 오류로 응답하면 예외를 던진다 (배치는 그날 실행을 실패로 남기고, 다음 실행이 되돌아보기로 따라잡는다)")
	void throwsOnServerError() {
		server.expect(requestTo(URL)).andRespond(withServerError());

		assertThatThrownBy(() -> adapter.findSoldEntries(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 28)))
			.isInstanceOf(RestClientException.class);
	}
}
