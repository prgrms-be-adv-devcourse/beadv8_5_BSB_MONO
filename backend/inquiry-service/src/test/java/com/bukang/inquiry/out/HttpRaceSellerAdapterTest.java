package com.bukang.inquiry.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bukang.inquiry.domain.exception.InquiryExternalServiceUnavailableException;
import com.bukang.inquiry.out.race.HttpRaceSellerAdapter;

/**
 * 상품 서비스 내부 API(GET /internal/v1/product/races/{raceId}/seller → RsData의 data {sellerMemberId})와 계약이 맞는지 확인한다.
 * 없는 대회만 빈 값이고, 알 수 없을 때는 503이다
 */
class HttpRaceSellerAdapterTest {
	private static final String URL = "http://product/internal/v1/product/races/5/seller";

	private MockRestServiceServer server;
	private HttpRaceSellerAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://product");
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new HttpRaceSellerAdapter(builder.build());
	}

	@Test
	@DisplayName("대회 판매자의 회원 ID를 돌려준다")
	void found() {
		server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(body("{\"sellerMemberId\": 42}"), MediaType.APPLICATION_JSON));

		assertThat(adapter.findSellerMemberId(5L)).contains(42L);
		server.verify();
	}

	@Test
	@DisplayName("없는 대회(404 RACE_NOT_FOUND)는 빈 값이다")
	void notFound() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
			.body("{\"status\":404,\"code\":\"RACE_NOT_FOUND\",\"message\":\"존재하지 않는 대회입니다.\",\"data\":null}")
			.contentType(MediaType.APPLICATION_JSON));

		assertThat(adapter.findSellerMemberId(5L)).isEmpty();
	}

	@Test
	@DisplayName("5xx·그 밖의 4xx·값이 없는 응답·대회 없음이 아닌 404는 빈 값이 아니라 503으로 던진다")
	void unknownIsUnavailable() {
		server.expect(requestTo(URL)).andRespond(withServerError());
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
		server.expect(requestTo(URL)).andRespond(withSuccess(body("null"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withSuccess(body("{}"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

		for (int i = 0; i < 5; i++) {
			assertThatThrownBy(() -> adapter.findSellerMemberId(5L))
				.isInstanceOf(InquiryExternalServiceUnavailableException.class);
		}
		server.verify();
	}

	private static String body(String data) {
		return "{\"status\":200,\"message\":\"대회 판매자를 조회했습니다.\",\"data\":" + data + "}";
	}
}
