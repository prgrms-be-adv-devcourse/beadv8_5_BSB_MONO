package com.bukang.payout.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 주문 서비스의 1차 정산용 판매 집계 내부 API(GET /internal/v1/order/sold-summary)를 부르는 어댑터 (PRO-224, 주문 API는 PRO-231)
 * 실제 주문 서비스 대신 MockRestServiceServer로 응답을 흉내 낸다
 */
class OrderSoldSummaryAdapterTest {
	private static final String BASE_URL = "http://order-service";
	private static final String URL = BASE_URL + "/internal/v1/order/sold-summary?raceId=5";

	private MockRestServiceServer server;
	private OrderSoldSummaryAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new OrderSoldSummaryAdapter(builder.build());
	}

	@Test
	@DisplayName("대회 ID를 쿼리로 보내고, 판매자 × 코스별 가격 합과 장수를 SoldSummary로 바꾼다")
	void mapsSoldSummaries() {
		server.expect(requestTo(URL))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("""
				{
					"status": 200,
					"message": "판매 집계를 조회했습니다.",
					"data": [
						{ "sellerId": 3, "raceId": 5, "raceCourseId": 11, "priceSum": 150000, "ticketCount": 3 },
						{ "sellerId": 8, "raceId": 5, "raceCourseId": 12, "priceSum": 70000, "ticketCount": 2 }
					]
				}
				""", MediaType.APPLICATION_JSON));

		assertThat(adapter.summarizeSold(5)).containsExactly(
			new SoldSummary(3, 5, 11, 150_000L, 3),
			new SoldSummary(8, 5, 12, 70_000L, 2)
		);
		server.verify();
	}

	@Test
	@DisplayName("판매가 없으면 빈 목록이다")
	void emptyList() {
		server.expect(requestTo(URL))
			.andRespond(withSuccess("""
				{ "status": 200, "message": "판매 집계를 조회했습니다.", "data": [] }
				""", MediaType.APPLICATION_JSON));

		assertThat(adapter.summarizeSold(5)).isEmpty();
	}

	@Test
	@DisplayName("응답에 data가 없으면 빈 목록으로 바꾸지 않고 예외를 던진다")
	void throwsOnMissingData() {
		server.expect(requestTo(URL))
			.andRespond(withSuccess("""
				{ "status": 200, "message": "판매 집계를 조회했습니다.", "data": null }
				""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> adapter.summarizeSold(5))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("raceId=5");
	}

	@Test
	@DisplayName("주문 서비스가 오류로 응답하면 예외를 던진다 (배치는 그 대회를 실패로 남기고 되돌아보기로 따라잡는다)")
	void throwsOnServerError() {
		server.expect(requestTo(URL)).andRespond(withServerError());

		assertThatThrownBy(() -> adapter.summarizeSold(5)).isInstanceOf(RestClientException.class);
	}
}
