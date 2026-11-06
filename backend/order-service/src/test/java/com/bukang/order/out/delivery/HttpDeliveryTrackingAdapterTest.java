package com.bukang.order.out.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.ConnectException;
import java.time.Duration;
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

import com.bukang.order.domain.DeliveryTrackingStatus;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;

class HttpDeliveryTrackingAdapterTest {
	private static final String BASE_URL = "https://tracker";
	private static final String URL = BASE_URL + "/graphql";
	private static final String CARRIER_ID = "kr.cjlogistics";
	private static final String TRACKING_NUMBER = "684912345678";
	// 2·4·8초 대신 기다리지 않는다
	private static final List<Duration> NO_WAIT = List.of(Duration.ZERO, Duration.ZERO, Duration.ZERO);

	private MockRestServiceServer server;
	private HttpDeliveryTrackingAdapter adapter;
	private RestClient.Builder builder;

	@BeforeEach
	void setUp() {
		builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new HttpDeliveryTrackingAdapter(builder.build(), "test-id", "test-secret", NO_WAIT);
	}

	@Test
	@DisplayName("Query.track을 키 헤더와 함께 보내고, 마지막 상태와 이동 기록을 한국 시각으로 돌려준다")
	void track() {
		server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
			.andExpect(header("Authorization", "TRACKQL-API-KEY test-id:test-secret"))
			.andExpect(jsonPath("$.variables.carrierId").value(CARRIER_ID))
			.andExpect(jsonPath("$.variables.trackingNumber").value(TRACKING_NUMBER))
			.andRespond(withSuccess("""
				{ "data": { "track": {
					"lastEvent": { "time": "2026-11-03T05:50:00.000Z",
						"status": { "code": "DELIVERED", "name": "배달완료" } },
					"events": { "edges": [
						{ "node": { "time": "2026-11-02T09:10:00+09:00",
							"status": { "code": "IN_TRANSIT", "name": "간선상차" },
							"description": "옥천HUB 출발", "location": { "name": "옥천HUB" } } },
						{ "node": { "time": "2026-11-03T05:50:00.000Z",
							"status": { "code": "DELIVERED", "name": "배달완료" },
							"description": "배달 완료", "location": null } },
						{ "node": { "time": null, "status": { "code": "NEW_CODE", "name": null },
							"description": null, "location": null } }
					] }
				} } }
				""", MediaType.APPLICATION_JSON));

		DeliveryTrackingResult result = adapter.track(CARRIER_ID, TRACKING_NUMBER);

		assertThat(result.status()).isEqualTo(DeliveryTrackingStatus.DELIVERED);
		assertThat(result.statusText()).isEqualTo("배달완료");
		assertThat(result.lastEventAt()).isEqualTo(LocalDateTime.of(2026, 11, 3, 14, 50));
		assertThat(result.events()).containsExactly(
			new DeliveryTrackingResult.Event(LocalDateTime.of(2026, 11, 2, 9, 10), DeliveryTrackingStatus.IN_TRANSIT,
				"간선상차", "옥천HUB 출발", "옥천HUB"),
			new DeliveryTrackingResult.Event(LocalDateTime.of(2026, 11, 3, 14, 50), DeliveryTrackingStatus.DELIVERED,
				"배달완료", "배달 완료", null),
			// 모르는 상태 코드는 UNKNOWN으로 받는다
			new DeliveryTrackingResult.Event(null, DeliveryTrackingStatus.UNKNOWN, null, null, null));
		server.verify();
	}

	@Test
	@DisplayName("없는 운송장(200 + errors NOT_FOUND)은 오류가 아니라 아직 조회되지 않음이다")
	void notFound() {
		server.expect(requestTo(URL)).andRespond(withSuccess("""
			{ "errors": [ { "message": "Not Found", "extensions": { "code": "NOT_FOUND" } } ],
				"data": { "track": null } }
			""", MediaType.APPLICATION_JSON));

		assertThat(adapter.track(CARRIER_ID, TRACKING_NUMBER)).isEqualTo(DeliveryTrackingResult.notTracked());
	}

	@Test
	@DisplayName("200이어도 errors가 NOT_FOUND가 아니면(인증·잘못된 요청) 503 예외다")
	void errorsWithOk() {
		server.expect(requestTo(URL)).andRespond(withSuccess("""
			{ "errors": [ { "message": "Unauthenticated", "extensions": { "code": "UNAUTHENTICATED" } } ] }
			""", MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withSuccess("""
			{ "errors": [ { "message": "bad", "extensions": { "code": "BAD_REQUEST" } } ], "data": null }
			""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> adapter.track(CARRIER_ID, TRACKING_NUMBER))
			.isInstanceOf(ExternalServiceUnavailableException.class);
		assertThatThrownBy(() -> adapter.track(CARRIER_ID, TRACKING_NUMBER))
			.isInstanceOf(ExternalServiceUnavailableException.class);
	}

	@Test
	@DisplayName("errors에 NOT_FOUND와 다른 오류가 섞이면 아직 조회되지 않음이 아니라 503 예외다")
	void mixedErrors() {
		server.expect(requestTo(URL)).andRespond(withSuccess("""
			{ "errors": [ { "message": "Not Found", "extensions": { "code": "NOT_FOUND" } },
				{ "message": "oops", "extensions": { "code": "INTERNAL" } } ] }
			""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> adapter.track(CARRIER_ID, TRACKING_NUMBER))
			.isInstanceOf(ExternalServiceUnavailableException.class);
	}

	@Test
	@DisplayName("5xx가 계속되면 3번 다시 불러 모두 4번 부른 뒤 503 예외다. 빈 본문은 다시 부르지 않는다")
	void serverError() {
		server.expect(times(4), requestTo(URL)).andRespond(withServerError());
		server.expect(once(), requestTo(URL)).andRespond(withSuccess("", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> adapter.track(CARRIER_ID, TRACKING_NUMBER))
			.isInstanceOf(ExternalServiceUnavailableException.class);
		assertThatThrownBy(() -> adapter.track(CARRIER_ID, TRACKING_NUMBER))
			.isInstanceOf(ExternalServiceUnavailableException.class);
		server.verify();
	}

	@Test
	@DisplayName("연결 실패·429·5xx 뒤에 성공하면 다시 불러 결과를 돌려준다 (BUK-90)")
	void retryThenSuccess() {
		server.expect(once(), requestTo(URL)).andRespond(withException(new ConnectException("refused")));
		server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
		server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
		server.expect(once(), requestTo(URL)).andRespond(withSuccess("""
			{ "errors": [ { "message": "Not Found", "extensions": { "code": "NOT_FOUND" } } ] }
			""", MediaType.APPLICATION_JSON));

		assertThat(adapter.track(CARRIER_ID, TRACKING_NUMBER)).isEqualTo(DeliveryTrackingResult.notTracked());
		server.verify();
	}

	@Test
	@DisplayName("4xx(429 말고)는 다시 불러도 같아 한 번만 부르고 503 예외다 (BUK-90)")
	void clientErrorNotRetried() {
		server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

		assertThatThrownBy(() -> adapter.track(CARRIER_ID, TRACKING_NUMBER))
			.isInstanceOf(ExternalServiceUnavailableException.class);
		server.verify();
	}

	@Test
	@DisplayName("키가 없으면 부르지 않고 503 예외다")
	void noKey() {
		HttpDeliveryTrackingAdapter withoutKey = new HttpDeliveryTrackingAdapter(builder.build(), "", null, NO_WAIT);

		assertThatThrownBy(() -> withoutKey.track(CARRIER_ID, TRACKING_NUMBER))
			.isInstanceOf(ExternalServiceUnavailableException.class);
		server.verify();
	}
}
