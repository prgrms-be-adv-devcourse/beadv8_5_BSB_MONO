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
 * 상품 서비스의 정산 대상 대회 내부 API를 부르는 어댑터 (PRO-224, 상품 API는 PRO-230)
 * 실제 상품 서비스 대신 MockRestServiceServer로 응답을 흉내 낸다
 */
class ProductRaceAdapterTest {
	private static final String BASE_URL = "http://product-service";
	private static final String BY_REFUND_DEADLINE_URL =
		BASE_URL + "/internal/v1/product/races/by-refund-deadline?from=2026-10-25&to=2026-10-31";
	private static final String BY_RACE_DATE_URL =
		BASE_URL + "/internal/v1/product/races/by-race-date?from=2026-10-25&to=2026-10-31";
	private static final LocalDate FROM = LocalDate.of(2026, 10, 25);
	private static final LocalDate TO = LocalDate.of(2026, 10, 31);

	private MockRestServiceServer server;
	private ProductRaceAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new ProductRaceAdapter(builder.build());
	}

	@Test
	@DisplayName("환불 마감일 기간을 쿼리로 보내고, 대회 일시는 날짜로 바꿔 RaceSchedule로 돌려준다")
	void mapsRacesByRefundDeadline() {
		server.expect(requestTo(BY_REFUND_DEADLINE_URL))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("""
				{
					"status": 200,
					"message": "정산 대상 대회를 조회했습니다.",
					"data": [
						{ "raceId": 1, "raceName": "서울 하프", "raceDate": "2026-11-01T08:00:00",
							"refundDeadline": "2026-10-25" },
						{ "raceId": 2, "raceName": "부산 10K", "raceDate": "2026-11-08T23:59:00",
							"refundDeadline": "2026-10-31" }
					]
				}
				""", MediaType.APPLICATION_JSON));

		List<RaceSchedule> races = adapter.findRacesByRefundDeadline(FROM, TO);

		assertThat(races).containsExactly(
			new RaceSchedule(1, "서울 하프", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 25)),
			new RaceSchedule(2, "부산 10K", LocalDate.of(2026, 11, 8), LocalDate.of(2026, 10, 31))
		);
		server.verify();
	}

	@Test
	@DisplayName("대회일 기간은 by-race-date 경로로 묻는다")
	void mapsRacesByRaceDate() {
		server.expect(requestTo(BY_RACE_DATE_URL))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("""
				{
					"status": 200,
					"message": "정산 대상 대회를 조회했습니다.",
					"data": [
						{ "raceId": 3, "raceName": "대구 마라톤", "raceDate": "2026-10-25T00:00:00",
							"refundDeadline": "2026-10-20" }
					]
				}
				""", MediaType.APPLICATION_JSON));

		assertThat(adapter.findRacesByRaceDate(FROM, TO)).containsExactly(
			new RaceSchedule(3, "대구 마라톤", LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 20)));
		server.verify();
	}

	@Test
	@DisplayName("대상 대회가 없으면 빈 목록이다")
	void emptyList() {
		server.expect(requestTo(BY_REFUND_DEADLINE_URL))
			.andRespond(withSuccess("""
				{ "status": 200, "message": "정산 대상 대회를 조회했습니다.", "data": [] }
				""", MediaType.APPLICATION_JSON));

		assertThat(adapter.findRacesByRefundDeadline(FROM, TO)).isEmpty();
	}

	@Test
	@DisplayName("응답에 data가 없으면 빈 목록으로 바꾸지 않고 예외를 던진다 (그날 정산을 조용히 건너뛰지 않게)")
	void throwsOnMissingData() {
		server.expect(requestTo(BY_RACE_DATE_URL))
			.andRespond(withSuccess("""
				{ "status": 200, "message": "정산 대상 대회를 조회했습니다.", "data": null }
				""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> adapter.findRacesByRaceDate(FROM, TO))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("응답이 비어 있습니다");
	}

	@Test
	@DisplayName("상품 서비스가 오류로 응답하면 예외를 던진다 (배치는 그날 실행을 실패로 남기고, 다음 실행이 되돌아보기로 따라잡는다)")
	void throwsOnServerError() {
		server.expect(requestTo(BY_REFUND_DEADLINE_URL)).andRespond(withServerError());

		assertThatThrownBy(() -> adapter.findRacesByRefundDeadline(FROM, TO))
			.isInstanceOf(RestClientException.class);
	}
}
