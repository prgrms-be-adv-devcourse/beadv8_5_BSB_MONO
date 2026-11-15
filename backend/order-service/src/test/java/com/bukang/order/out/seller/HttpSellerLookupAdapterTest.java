package com.bukang.order.out.seller;

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

class HttpSellerLookupAdapterTest {
	private static final String BASE_URL = "http://member";
	private static final long MEMBER_ID = 3L;
	private static final String URL = BASE_URL + "/internal/v1/member/members/3/seller";

	private MockRestServiceServer server;
	private HttpSellerLookupAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new HttpSellerLookupAdapter(builder.build());
	}

	@Test
	@DisplayName("승인된 판매자면 회원 서비스가 준 판매자 ID를 돌려준다")
	void approved() {
		server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(response("21", "\"APPROVED\""), MediaType.APPLICATION_JSON));

		assertThat(adapter.findApprovedSellerId(MEMBER_ID)).contains(21L);
		server.verify();
	}

	@Test
	@DisplayName("승인 대기·반려·신청 없음이면 판매자가 아니다 (빈 값 → 주문이 403)")
	void notApproved() {
		server.expect(requestTo(URL)).andRespond(withSuccess(response("21", "\"PENDING\""), MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withSuccess(response("21", "\"REJECTED\""), MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withSuccess(response("null", "null"), MediaType.APPLICATION_JSON));

		for (int i = 0; i < 3; i++) {
			assertThat(adapter.findApprovedSellerId(MEMBER_ID)).isEmpty();
		}
		server.verify();
	}

	@Test
	@DisplayName("404·5xx·시간 초과·빈 응답·판매자 ID 없는 승인이면 판매자인지 모르므로 503 예외로 알린다")
	void unavailable() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
			.contentType(MediaType.APPLICATION_JSON)
			.body("{ \"status\": 404, \"code\": \"MEMBER_NOT_FOUND\", \"message\": \"존재하지 않는 회원입니다.\" }"));
		server.expect(requestTo(URL)).andRespond(withServerError());
		server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("read timed out")));
		server.expect(requestTo(URL)).andRespond(withSuccess(
			"{ \"status\": 200, \"message\": \"조회\", \"data\": null }", MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withSuccess(response("null", "\"APPROVED\""),
			MediaType.APPLICATION_JSON));

		for (int i = 0; i < 5; i++) {
			assertThatThrownBy(() -> adapter.findApprovedSellerId(MEMBER_ID))
				.isInstanceOf(ExternalServiceUnavailableException.class)
				.hasMessage(HttpSellerLookupAdapter.UNAVAILABLE_MESSAGE);
		}
		server.verify();
	}

	// 회원 서비스 InternalV1MemberController가 돌려주는 모양 (MemberSellerDto)
	private String response(String sellerId, String status) {
		return """
			{ "status": 200, "message": "판매자 신청을 조회했습니다.", "data": { "sellerId": %s, "status": %s } }
			""".formatted(sellerId, status);
	}
}
