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
import com.bukang.inquiry.out.member.HttpMemberSellerAdapter;

/**
 * 회원 서비스 내부 API(GET /internal/v1/member/members/{memberId}/seller → RsData의 data {sellerId, status})와 계약이 맞는지 확인한다.
 * 승인(APPROVED)일 때만 판매자다. 알 수 없을 때는 false가 아니라 503이다
 */
class HttpMemberSellerAdapterTest {
	private static final String URL = "http://member/internal/v1/member/members/3/seller";

	private MockRestServiceServer server;
	private HttpMemberSellerAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://member");
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new HttpMemberSellerAdapter(builder.build());
	}

	@Test
	@DisplayName("승인된 판매자 신청만 판매자이고, 승인 대기·반려는 판매자가 아니다")
	void onlyApproved() {
		server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(body("{\"sellerId\": 7, \"status\": \"APPROVED\"}"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL))
			.andRespond(withSuccess(body("{\"sellerId\": 7, \"status\": \"PENDING\"}"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL))
			.andRespond(withSuccess(body("{\"sellerId\": 7, \"status\": \"REJECTED\"}"), MediaType.APPLICATION_JSON));

		assertThat(adapter.isApprovedSeller(3L)).isTrue();
		assertThat(adapter.isApprovedSeller(3L)).isFalse();
		assertThat(adapter.isApprovedSeller(3L)).isFalse();
		server.verify();
	}

	@Test
	@DisplayName("없거나 탈퇴한 회원(404)은 판매자가 아니다")
	void notFound() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThat(adapter.isApprovedSeller(3L)).isFalse();
	}

	@Test
	@DisplayName("5xx·값이 없는 응답은 판매자로 보지 않고 503으로 던진다")
	void unknownIsUnavailable() {
		server.expect(requestTo(URL)).andRespond(withServerError());
		server.expect(requestTo(URL)).andRespond(withSuccess(body("null"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withSuccess(body("{}"), MediaType.APPLICATION_JSON));

		for (int i = 0; i < 3; i++) {
			assertThatThrownBy(() -> adapter.isApprovedSeller(3L))
				.isInstanceOf(InquiryExternalServiceUnavailableException.class);
		}
	}

	private static String body(String data) {
		return "{\"status\":200,\"message\":\"조회\",\"data\":" + data + "}";
	}
}
