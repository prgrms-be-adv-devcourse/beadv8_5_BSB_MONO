package com.bukang.order.out.race;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bukang.order.domain.exception.ExternalServiceUnavailableException;

class HttpRaceSellerAdapterTest {
	private static final String BASE_URL = "http://product";
	private static final long RACE_ID = 5L;
	private static final String URL = BASE_URL + "/internal/v1/product/races/5/seller";

	private MockRestServiceServer server;
	private HttpRaceSellerAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new HttpRaceSellerAdapter(builder.build());
	}

	@Test
	@DisplayName("대회 ID로 묻고 판매자의 회원 ID를 돌려준다")
	void found() {
		server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(
			"{ \"status\": 200, \"message\": \"대회 판매자를 조회했습니다.\", \"data\": { \"sellerMemberId\": 7 } }",
			MediaType.APPLICATION_JSON));

		assertThat(adapter.findSellerMemberId(RACE_ID)).contains(7L);
		server.verify();
	}

	@Test
	@DisplayName("없는 대회(404 RACE_NOT_FOUND)면 빈 값이다")
	void notFound() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
			.contentType(MediaType.APPLICATION_JSON)
			.body("{ \"status\": 404, \"code\": \"RACE_NOT_FOUND\", \"message\": \"대회를 찾을 수 없습니다.\" }"));

		assertThat(adapter.findSellerMemberId(RACE_ID)).isEmpty();
	}

	@Test
	@DisplayName("대회 없음이 아닌 404(경로가 어긋남)는 빈 값으로 보지 않고 503 예외로 알린다")
	void notFoundWithoutRaceCode() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> adapter.findSellerMemberId(RACE_ID))
			.isInstanceOf(ExternalServiceUnavailableException.class);
	}

	@Test
	@DisplayName("5xx·시간 초과·그 밖의 4xx·빈 응답이면 503 예외로 알린다")
	void unavailable() {
		server.expect(requestTo(URL)).andRespond(withServerError());
		server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("read timed out")));
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
		server.expect(requestTo(URL)).andRespond(withSuccess(
			"{ \"status\": 200, \"message\": \"조회\", \"data\": {} }", MediaType.APPLICATION_JSON));

		for (int i = 0; i < 4; i++) {
			assertThatThrownBy(() -> adapter.findSellerMemberId(RACE_ID))
				.isInstanceOf(ExternalServiceUnavailableException.class);
		}
		server.verify();
	}
}
