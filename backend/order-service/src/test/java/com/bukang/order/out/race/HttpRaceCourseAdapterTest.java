package com.bukang.order.out.race;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.out.InternalHttpClients;

class HttpRaceCourseAdapterTest {
	private static final String BASE_URL = "http://product";
	private static final String URL = BASE_URL + "/internal/v1/product/courses/7";

	private MockRestServiceServer server;
	private HttpRaceCourseAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new HttpRaceCourseAdapter(builder.build());
	}

	@Test
	@DisplayName("종목 ID로 묻고, 응답을 주문 RaceCourseInfo로 옮긴다 (없을 수 있는 최대 나이·일정 변경 일시는 null)")
	void findCourse() {
		server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(response("ON_SALE", "null", "null"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL))
			.andRespond(withSuccess(response("SCHEDULED", "60", "\"2026-10-20T09:00:00\""),
				MediaType.APPLICATION_JSON));

		RaceCourseInfo info = adapter.findCourse(7).orElseThrow();
		assertThat(info).isEqualTo(new RaceCourseInfo(7, 3, LocalDateTime.of(2026, 11, 15, 8, 0),
			LocalDateTime.of(2026, 10, 1, 10, 0), LocalDateTime.of(2026, 11, 1, 23, 59, 59), 14, null, 9,
			"2026 서울 하프 마라톤 10K", 50_000, true, LocalDate.of(2026, 11, 8), null, false,
			List.of("S", "M", "L")));
		RaceCourseInfo changed = adapter.findCourse(7).orElseThrow();
		assertThat(changed.maxAge()).isEqualTo(60);
		assertThat(changed.scheduleChangedAt()).isEqualTo(LocalDateTime.of(2026, 10, 20, 9, 0));
		server.verify();
	}

	@Test
	@DisplayName("파티 예매 칸: 최대 인원이 비면 제한 없음(null), 정한 값은 그대로(20명도), 기간이 비면 모르는 것(null)으로 둔다")
	void crewSale() {
		server.expect(requestTo(URL)).andRespond(withSuccess(withCrew(5, "null"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withSuccess(withCrew(5, "20"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withSuccess(withCrew(5, "8"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL))
			.andRespond(withSuccess(response("ON_SALE", "null", "null"), MediaType.APPLICATION_JSON));

		RaceCourseInfo.CrewSale noCap = adapter.findCourse(7).orElseThrow().crewSale();
		assertThat(noCap).isEqualTo(new RaceCourseInfo.CrewSale(LocalDateTime.of(2026, 9, 25, 10, 0),
			LocalDateTime.of(2026, 9, 30, 19, 0), 5, null));
		assertThat(adapter.findCourse(7).orElseThrow().crewSale().maxCount()).isEqualTo(20);
		assertThat(adapter.findCourse(7).orElseThrow().crewSale().maxCount()).isEqualTo(8);
		assertThat(adapter.findCourse(7).orElseThrow().crewSale()).isNull();
		server.verify();
	}

	@Test
	@DisplayName("없는 종목(404)은 찾지 못한 것으로 보고, 취소된 대회(CANCELED)는 canceled로, 판매 중지(STOPPED)는 그대로 옮긴다")
	void notFoundOrCanceled() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
		server.expect(requestTo(URL))
			.andRespond(withSuccess(response("CANCELED", "null", "null"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL))
			.andRespond(withSuccess(response("STOPPED", "null", "null"), MediaType.APPLICATION_JSON));

		assertThat(adapter.findCourse(7)).isEmpty();
		assertThat(adapter.findCourse(7).orElseThrow().canceled()).isTrue();
		assertThat(adapter.findCourse(7).orElseThrow().canceled()).isFalse();
		server.verify();
	}

	@Test
	@DisplayName("5xx·응답 지연·404가 아닌 4xx·값이 빠진 응답은 판정할 수 없으므로 503으로 알린다")
	void unavailable() {
		server.expect(requestTo(URL)).andRespond(withServerError());
		server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("read timed out")));
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
		server.expect(requestTo(URL)).andRespond(withSuccess(
			"{ \"status\": 200, \"message\": \"조회\", \"data\": null }", MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withSuccess(
			"{ \"status\": 200, \"message\": \"조회\", \"data\": { \"raceCourseId\": 7 } }", MediaType.APPLICATION_JSON));

		for (int attempt = 0; attempt < 5; attempt++) {
			assertThatThrownBy(() -> adapter.findCourse(7))
				.isInstanceOf(ExternalServiceUnavailableException.class)
				.hasMessage(HttpRaceCourseAdapter.UNAVAILABLE_MESSAGE);
		}
		server.verify();
	}

	@Test
	@DisplayName("상품 서비스가 시간 제한 안에 답하지 않으면 기다리지 않고 503 예외로 알린다 (실제 연결로 확인)")
	void timeout() throws Exception {
		try (ServerSocket silentServer = new ServerSocket(0)) {
			HttpRaceCourseAdapter slowAdapter = new HttpRaceCourseAdapter(
				InternalHttpClients.create("http://localhost:" + silentServer.getLocalPort(), Duration.ofMillis(300)));
			long startedAt = System.nanoTime();

			assertThatThrownBy(() -> slowAdapter.findCourse(7))
				.isInstanceOf(ExternalServiceUnavailableException.class);
			assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(2));
		}
	}

	// 파티 예매 칸이 있는 응답 (상품 PR #356 RaceCourseInfoDto)
	private String withCrew(int minCount, String maxCount) {
		return response("ON_SALE", "null", "null").replace("\"tshirtSizeOption\"",
			"\"crewSaleStartAt\": \"2026-09-25T10:00:00\", \"crewSaleEndAt\": \"2026-09-30T19:00:00\", "
				+ "\"crewMinCount\": %d, \"crewMaxCount\": %s, \"tshirtSizeOption\"".formatted(minCount, maxCount));
	}

	// 상품 서비스 InternalV1RaceCourseController가 실제로 돌려주는 모양 (data = RaceCourseInfoDto)
	private String response(String saleStatus, String maxAge, String scheduleChangedAt) {
		return """
			{ "status": 200, "message": "종목 정보를 조회했습니다.",
				"data": { "raceCourseId": 7, "raceId": 3, "title": "2026 서울 하프 마라톤",
					"raceDate": "2026-11-15T08:00:00", "applicationOpenAt": "2026-10-01T10:00:00",
					"applicationCloseAt": "2026-11-01T23:59:59", "minAge": 14, "maxAge": %s, "sellerId": 9,
					"course": "10K", "courseName": "2026 서울 하프 마라톤 10K", "price": 50000, "kitPrice": 5000,
					"refundDeadline": "2026-11-08", "scheduleChangedAt": %s, "saleStatus": "%s",
					"courseStatus": "OPEN", "individualRemaining": 100, "crewRemaining": 0, "kitIncluded": true,
					"tshirtSizeOption": ["S", "M", "L"] } }
			""".formatted(maxAge, scheduleChangedAt, saleStatus);
	}
}
