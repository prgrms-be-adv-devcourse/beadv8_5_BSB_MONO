package com.bukang.member.out.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.YearMonth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bukang.member.crew.domain.exception.CrewPenaltyLookupFailedException;

// 상품 서비스 「결제율 미달 크루 조회」 내부 API 클라이언트 (BUK-187)
class HttpLowPaymentCrewAdapterTest {
	private static final String BASE_URL = "http://product";
	private static final String URL = BASE_URL + "/internal/v1/product/crews/low-payment?month=2026-11";
	private static final YearMonth MONTH = YearMonth.of(2026, 11);

	private MockRestServiceServer server;
	private HttpLowPaymentCrewAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new HttpLowPaymentCrewAdapter(builder.build());
	}

	@Test
	@DisplayName("달을 2026-11 형식으로 보내고, 응답의 crewId·lowPaymentCount만 읽는다 (모르는 필드는 무시)")
	void findLowPaymentCrews() {
		server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("""
				{
					"status": 200,
					"message": "결제율 미달 크루를 조회했습니다.",
					"data": [
						{ "crewId": 7, "lowPaymentCount": 3, "lowPaymentParties": [ { "partyId": 1 } ] },
						{ "crewId": 9, "lowPaymentCount": 4, "lowPaymentParties": [] }
					]
				}
				""", MediaType.APPLICATION_JSON));

		assertThat(adapter.findLowPaymentCrews(MONTH))
			.containsExactly(new LowPaymentCrew(7L, 3), new LowPaymentCrew(9L, 4));
		server.verify();
	}

	@Test
	@DisplayName("미달 크루가 없으면 빈 목록이다")
	void empty() {
		server.expect(requestTo(URL)).andRespond(withSuccess("""
			{ "status": 200, "message": "조회", "data": [] }
			""", MediaType.APPLICATION_JSON));

		assertThat(adapter.findLowPaymentCrews(MONTH)).isEmpty();
	}

	@Test
	@DisplayName("연결 실패·시간 초과·4xx·5xx·data 없는 응답은 예외로 알린다 (그 달만 건너뛸 수 있게)")
	void unavailable() {
		server.expect(requestTo(URL)).andRespond(withException(new ConnectException("connection refused")));
		server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("read timed out")));
		server.expect(requestTo(URL)).andRespond(withBadRequest());
		server.expect(requestTo(URL)).andRespond(withServerError());
		server.expect(requestTo(URL)).andRespond(withSuccess("""
			{ "status": 200, "message": "조회", "data": null }
			""", MediaType.APPLICATION_JSON));

		for (int attempt = 0; attempt < 5; attempt++) {
			assertThatThrownBy(() -> adapter.findLowPaymentCrews(MONTH))
				.isInstanceOf(CrewPenaltyLookupFailedException.class);
		}
		server.verify();
	}
}
