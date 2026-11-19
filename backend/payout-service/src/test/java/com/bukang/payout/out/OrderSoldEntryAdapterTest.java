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

import com.bukang.payout.domain.exception.PayoutOrderLookupUnavailableException;

/**
 * 주문 서비스의 판매 내역 내부 API(GET /internal/v1/order/sold-entries)를 부르는 어댑터 (PRO-62, 코스 묶음 BUK-244)
 * 실제 주문 서비스 대신 MockRestServiceServer로 응답을 흉내 낸다
 */
class OrderSoldEntryAdapterTest {
	private static final String BASE_URL = "http://order-service";
	private static final String URL = BASE_URL + "/internal/v1/order/sold-entries?raceId=5";

	private MockRestServiceServer server;
	private OrderSoldEntryAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new OrderSoldEntryAdapter(builder.build());
	}

	@Test
	@DisplayName("대회 ID를 쿼리로 보내고, 코스 묶음을 풀어 주문 상품 ID 순 SoldEntry로 바꾼다")
	void mapsCourseGroups() {
		server.expect(requestTo(URL))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("""
				{
					"status": 200,
					"message": "판매 내역을 조회했습니다.",
					"data": {
						"raceId": 5, "raceDate": "2026-09-22", "sellerId": 3,
						"courses": [
							{ "raceCourseId": 11, "price": 60000, "paidIds": [1, 7], "blockingIds": [4] },
							{ "raceCourseId": 12, "price": 55000, "paidIds": [2], "blockingIds": [] }
						]
					}
				}
				""", MediaType.APPLICATION_JSON));

		List<SoldEntry> entries = adapter.findSoldEntries(5);

		LocalDate raceDate = LocalDate.of(2026, 9, 22);
		assertThat(entries).containsExactly(
			new SoldEntry(1, 3, 5, 11L, raceDate, 60_000L, SoldEntryStatus.결제_완료),
			new SoldEntry(2, 3, 5, 12L, raceDate, 55_000L, SoldEntryStatus.결제_완료),
			new SoldEntry(4, 3, 5, 11L, raceDate, 60_000L, SoldEntryStatus.취소_중),
			new SoldEntry(7, 3, 5, 11L, raceDate, 60_000L, SoldEntryStatus.결제_완료)
		);
		server.verify();
	}

	@Test
	@DisplayName("판매가 없으면 courses가 빈 배열이고 빈 목록이다")
	void emptyCourses() {
		server.expect(requestTo(URL))
			.andRespond(withSuccess("""
				{ "status": 200, "message": "판매 내역을 조회했습니다.",
					"data": { "raceId": 5, "raceDate": null, "sellerId": null, "courses": [] } }
				""", MediaType.APPLICATION_JSON));

		assertThat(adapter.findSoldEntries(5)).isEmpty();
	}

	@Test
	@DisplayName("courses 칸이 없으면(옛 응답 모양 등) 빈 목록으로 삼키지 않고 예외를 던진다")
	void missingCoursesThrows() {
		server.expect(requestTo(URL))
			.andRespond(withSuccess("""
				{ "status": 200, "message": "판매 내역을 조회했습니다.", "data": { "raceId": 5 } }
				""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> adapter.findSoldEntries(5))
			.isInstanceOf(PayoutOrderLookupUnavailableException.class);
	}

	@Test
	@DisplayName("묶음이 있는데 판매자나 대회일이 비면 짐작하지 않고 예외를 던진다")
	void missingSellerThrows() {
		server.expect(requestTo(URL))
			.andRespond(withSuccess("""
				{ "status": 200, "message": "판매 내역을 조회했습니다.",
					"data": { "raceId": 5, "raceDate": "2026-09-22",
						"courses": [ { "raceCourseId": 11, "price": 60000, "paidIds": [1], "blockingIds": [] } ] } }
				""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> adapter.findSoldEntries(5))
			.isInstanceOf(PayoutOrderLookupUnavailableException.class);
	}

	@Test
	@DisplayName("주문 서비스가 오류로 응답하면 예외를 던진다 (배치는 그 대회를 실패로 남기고, 다음 실행이 되돌아보기로 따라잡는다)")
	void throwsOnServerError() {
		server.expect(requestTo(URL)).andRespond(withServerError());

		assertThatThrownBy(() -> adapter.findSoldEntries(5))
			.isInstanceOf(PayoutOrderLookupUnavailableException.class)
			.hasCauseInstanceOf(RestClientException.class);
	}
}
