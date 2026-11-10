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
import com.bukang.inquiry.out.member.HttpMemberAdminAdapter;

/**
 * 회원 서비스 내부 API(GET /internal/v1/member/members/{memberId}/admin → RsData의 data.admin)와 계약이 맞는지 확인한다.
 * 알 수 없을 때는 false가 아니라 503이다. 모르는 경우를 관리자로 보면 회원 서비스가 멈춘 동안 누구나 관리자가 되기 때문이다
 */
class HttpMemberAdminAdapterTest {
	private static final String URL = "http://member/internal/v1/member/members/3/admin";

	private MockRestServiceServer server;
	private HttpMemberAdminAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://member");
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new HttpMemberAdminAdapter(builder.build());
	}

	@Test
	@DisplayName("회원 ID로 묻고, 응답 data의 admin 값을 그대로 돌려준다")
	void admin() {
		server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(body("{\"admin\": true}"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL))
			.andRespond(withSuccess(body("{\"admin\": false}"), MediaType.APPLICATION_JSON));

		assertThat(adapter.isAdmin(3L)).isTrue();
		assertThat(adapter.isAdmin(3L)).isFalse();
		server.verify();
	}

	@Test
	@DisplayName("없거나 탈퇴한 회원(404 MEMBER_NOT_FOUND)은 관리자가 아니다")
	void memberNotFound() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
			.body("{\"status\":404,\"code\":\"MEMBER_NOT_FOUND\",\"message\":\"없음\",\"data\":null}"));

		assertThat(adapter.isAdmin(3L)).isFalse();
	}

	@Test
	@DisplayName("5xx·코드가 다른 404·값이 없는 응답은 관리자로 보지 않고 503으로 던진다")
	void unknownIsUnavailable() {
		server.expect(requestTo(URL)).andRespond(withServerError());
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
		server.expect(requestTo(URL)).andRespond(withSuccess(body("null"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withSuccess(body("{}"), MediaType.APPLICATION_JSON));

		for (int i = 0; i < 4; i++) {
			assertThatThrownBy(() -> adapter.isAdmin(3L)).isInstanceOf(InquiryExternalServiceUnavailableException.class);
		}
	}

	private static String body(String data) {
		return "{\"status\":200,\"message\":\"조회\",\"data\":" + data + "}";
	}
}
