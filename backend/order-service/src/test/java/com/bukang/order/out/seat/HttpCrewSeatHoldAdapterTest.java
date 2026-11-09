package com.bukang.order.out.seat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.domain.exception.ParticipationNotPayableException;
import com.bukang.order.domain.exception.RaceCourseNotFoundException;
import com.bukang.order.out.seat.CrewSeatHoldPort.CrewSeatHoldResult;
import com.bukang.order.out.seat.CrewSeatHoldPort.Seat;
import com.bukang.order.out.seat.CrewSeatHoldPort.Shortage;

class HttpCrewSeatHoldAdapterTest {
	private static final String BASE_URL = "http://product";
	private static final String URL = BASE_URL + "/internal/v1/product/crew-holds";
	private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 11, 13, 0, 0);
	private static final List<Seat> SEATS = List.of(new Seat(1001, 10), new Seat(1002, 10));

	private MockRestServiceServer server;
	private HttpCrewSeatHoldAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new HttpCrewSeatHoldAdapter(builder.build());
	}

	@Test
	@DisplayName("파티·크루·대회 ID, 결제 기한, 자리 목록을 보내고 확보면 기한을, 모자라면(200 held=false) 모자란 종목을 돌려준다")
	void holdAll() {
		server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
			.andExpect(content().json("""
				{ "partyId": 31, "crewId": 7, "raceId": 2, "expiresAt": "2026-11-13T00:00:00",
				  "seats": [ { "participationId": 1001, "raceCourseId": 10 },
				             { "participationId": 1002, "raceCourseId": 10 } ] }
				""", true))
			.andRespond(withSuccess(data("""
				{ "held": true, "expiresAt": "2026-11-13T00:00:00", "shortages": [] }
				"""), MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withSuccess(data("""
			{ "held": false, "expiresAt": null,
			  "shortages": [ { "raceCourseId": 10, "needed": 2, "remaining": 1 } ] }
			"""), MediaType.APPLICATION_JSON));

		assertThat(adapter.holdAll(31, 7, 2, EXPIRES_AT, SEATS)).isEqualTo(CrewSeatHoldResult.held(EXPIRES_AT));
		assertThat(adapter.holdAll(31, 7, 2, EXPIRES_AT, SEATS))
			.isEqualTo(CrewSeatHoldResult.shortOf(Map.of(10L, new Shortage(2, 1))));
		server.verify();
	}

	@Test
	@DisplayName("없는 대회·종목(404)은 종목 없음, 409(크루 예매 기간 아님·다른 자리 목록 등)는 상품의 이유 그대로 409로 알린다")
	void rejected() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.CONFLICT)
			.contentType(MediaType.APPLICATION_JSON)
			.body("{ \"status\": 409, \"message\": \"이미 다른 자리 목록으로 확보한 파티입니다.\", \"data\": null }"));
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.CONFLICT));

		assertThatThrownBy(() -> adapter.holdAll(31, 7, 2, EXPIRES_AT, SEATS))
			.isInstanceOf(RaceCourseNotFoundException.class);
		assertThatThrownBy(() -> adapter.holdAll(31, 7, 2, EXPIRES_AT, SEATS))
			.isInstanceOf(ParticipationNotPayableException.class)
			.hasMessage("이미 다른 자리 목록으로 확보한 파티입니다.");
		assertThatThrownBy(() -> adapter.holdAll(31, 7, 2, EXPIRES_AT, SEATS))
			.isInstanceOf(ParticipationNotPayableException.class)
			.hasMessage(HttpCrewSeatHoldAdapter.REJECTED_MESSAGE);
		server.verify();
	}

	@Test
	@DisplayName("5xx·응답 지연·400·형식이 다른 응답은 503으로 알린다 (같은 파티로 다시 하면 처음 결과를 받는다)")
	void unavailable() {
		server.expect(requestTo(URL)).andRespond(withServerError());
		server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("read timed out")));
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
		server.expect(requestTo(URL)).andRespond(withSuccess(data("{ \"held\": true }"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withSuccess(data("null"), MediaType.APPLICATION_JSON));

		for (int attempt = 0; attempt < 5; attempt++) {
			assertThatThrownBy(() -> adapter.holdAll(31, 7, 2, EXPIRES_AT, SEATS))
				.isInstanceOf(ExternalServiceUnavailableException.class)
				.hasMessage(HttpSeatHoldAdapter.UNAVAILABLE_MESSAGE);
		}
		server.verify();
	}

	private String data(String body) {
		return "{ \"status\": 200, \"message\": \"처리\", \"data\": " + body + " }";
	}
}
