package com.bukang.order.out.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bukang.order.domain.exception.ExternalServiceUnavailableException;

class HttpQueueAdmissionAdapterTest {
	private static final String BASE_URL = "http://queue";
	private static final long MEMBER_ID = 3;
	private static final long RACE_ID = 1;
	private static final long RACE_COURSE_ID = 11;
	private static final String URL = BASE_URL + "/internal/v1/queue/races/1/racecourses/11";
	// 대기열 시계는 주문과 다를 수 있다. 끝 시각은 남은 시간으로만 센다
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 16, 14, 0, 0, 123_456_789);
	private static final String ADMITTED = """
		{ "status": 200, "message": "대기열에 입장 중입니다.",
			"data": { "admitted": true, "admissionEndsAt": "2026-10-16T05:09:00", "remainingMillis": 540000 } }
		""";
	private static final String NOT_ADMITTED = """
		{ "status": 200, "message": "대기열에 입장 중이 아닙니다.",
			"data": { "admitted": false, "admissionEndsAt": null, "remainingMillis": 0 } }
		""";

	private MockRestServiceServer server;
	private HttpQueueAdmissionAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new HttpQueueAdmissionAdapter(builder.build());
	}

	@Test
	@DisplayName("입장 중이면 주문의 지금에 남은 시간을 더한 시각을 돌려준다 (대기열 시계의 끝 시각은 쓰지 않는다)")
	void admitted() {
		server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
			.andExpect(header("X-Member-Id", String.valueOf(MEMBER_ID)))
			.andRespond(withSuccess(ADMITTED, MediaType.APPLICATION_JSON));

		assertThat(adapter.findAdmissionEndsAt(MEMBER_ID, RACE_ID, RACE_COURSE_ID, NOW))
			.contains(LocalDateTime.of(2026, 10, 16, 14, 9, 0, 123_456_000));
		server.verify();
	}

	@Test
	@DisplayName("입장 중이 아니면 빈 값이다 (주문이 403 입장 전으로 알린다)")
	void notAdmitted() {
		server.expect(requestTo(URL)).andRespond(withSuccess(NOT_ADMITTED, MediaType.APPLICATION_JSON));

		assertThat(adapter.findAdmissionEndsAt(MEMBER_ID, RACE_ID, RACE_COURSE_ID, NOW)).isEmpty();
	}

	@Test
	@DisplayName("대기열이 5xx·4xx로 답하거나 응답이 늦으면 입장 전이 아니라 503 예외로 알린다")
	void unavailable() {
		server.expect(requestTo(URL)).andRespond(withServerError());
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
		server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("read timed out")));

		for (int i = 0; i < 3; i++) {
			assertThatThrownBy(() -> adapter.findAdmissionEndsAt(MEMBER_ID, RACE_ID, RACE_COURSE_ID, NOW))
				.isInstanceOf(ExternalServiceUnavailableException.class)
				.hasMessage(HttpQueueAdmissionAdapter.UNAVAILABLE_MESSAGE);
		}
	}

	@Test
	@DisplayName("응답이 JSON이 아니거나, 입장 여부가 없거나, 입장 중인데 남은 시간이 없으면 503 예외로 알린다")
	void malformed() {
		server.expect(requestTo(URL)).andRespond(withSuccess("<html>gateway error</html>", MediaType.TEXT_HTML));
		server.expect(requestTo(URL)).andRespond(withSuccess(
			"{ \"status\": 200, \"message\": \"확인\", \"data\": null }", MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withSuccess(
			"{ \"status\": 200, \"message\": \"확인\", \"data\": {} }", MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withSuccess(
			"{ \"status\": 200, \"message\": \"확인\", \"data\": { \"admitted\": true, \"remainingMillis\": 0 } }",
			MediaType.APPLICATION_JSON));

		for (int i = 0; i < 4; i++) {
			assertThatThrownBy(() -> adapter.findAdmissionEndsAt(MEMBER_ID, RACE_ID, RACE_COURSE_ID, NOW))
				.isInstanceOf(ExternalServiceUnavailableException.class);
		}
	}

	@Test
	@DisplayName("결제 페이지를 열 때도 같은 내부 API로 묻는다 (가짜처럼 다시 입장시키지 않는다)")
	void pageOpenAsksSameApi() {
		server.expect(requestTo(URL)).andRespond(withSuccess(NOT_ADMITTED, MediaType.APPLICATION_JSON));

		assertThat(adapter.findAdmissionEndsAtOnPageOpen(MEMBER_ID, RACE_ID, RACE_COURSE_ID, NOW)).isEmpty();
		server.verify();
	}
}
