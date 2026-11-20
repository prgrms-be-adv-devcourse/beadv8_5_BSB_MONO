package com.bukang.order.out.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.domain.exception.IdentityVerificationRequiredException;
import com.bukang.order.out.member.MemberIdentityPort.MemberIdentity;

class HttpMemberIdentityAdapterTest {
	private static final String BASE_URL = "http://member";
	private static final String URL = BASE_URL + "/api/v1/member/members/me";

	private MockRestServiceServer server;
	private HttpMemberIdentityAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new HttpMemberIdentityAdapter(builder.build());
	}

	@Test
	@DisplayName("회원 ID 헤더를 넣어 내 정보를 묻고, 이름·생년월일·휴대폰을 돌려준다")
	void find() {
		server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET)).andExpect(header("X-Member-Id", "3"))
			.andRespond(withSuccess("""
				{ "status": 200, "message": "내 정보를 조회했습니다.", "data": { "id": 3, "nickname": "러너",
					"phone": "010-1234-5678", "name": "김러너", "birthDate": "1995-04-12", "passwordSet": true } }
				""", MediaType.APPLICATION_JSON));

		assertThat(adapter.findVerified(3L))
			.isEqualTo(new MemberIdentity("김러너", LocalDate.of(1995, 4, 12), "010-1234-5678"));
		server.verify();
	}

	@Test
	@DisplayName("본인인증을 하지 않아 휴대폰이 비었으면 409로 본인인증을 먼저 하게 한다")
	void notVerified() {
		server.expect(requestTo(URL)).andRespond(withSuccess("""
			{ "status": 200, "message": "조회", "data": { "name": "김러너", "birthDate": "1995-04-12", "phone": null } }
			""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> adapter.findVerified(3L)).isInstanceOf(IdentityVerificationRequiredException.class);
	}

	@Test
	@DisplayName("5xx·404·빈 응답은 503 예외로 알린다")
	void unavailable() {
		server.expect(requestTo(URL)).andRespond(withServerError());
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
		server.expect(requestTo(URL)).andRespond(withSuccess(
			"{ \"status\": 200, \"message\": \"조회\", \"data\": null }", MediaType.APPLICATION_JSON));

		for (int i = 0; i < 3; i++) {
			assertThatThrownBy(() -> adapter.find(3L)).isInstanceOf(ExternalServiceUnavailableException.class)
				.hasMessage(HttpMemberIdentityAdapter.UNAVAILABLE_MESSAGE);
		}
	}
}
