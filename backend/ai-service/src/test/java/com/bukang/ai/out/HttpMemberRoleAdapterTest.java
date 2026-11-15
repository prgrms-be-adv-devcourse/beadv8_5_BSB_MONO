package com.bukang.ai.out;

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
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

import com.bukang.ai.domain.exception.AiExternalServiceUnavailableException;
import com.bukang.ai.out.member.HttpMemberRoleAdapter;

/**
 * 회원 서비스 내부 API(.../admin → data.admin, .../seller → data.status)와 계약이 맞는지 확인한다
 * 알 수 없을 때는 false가 아니라 503이다
 */
class HttpMemberRoleAdapterTest {
	private static final String ADMIN_URL = "http://member/internal/v1/member/members/3/admin";
	private static final String SELLER_URL = "http://member/internal/v1/member/members/3/seller";
	private static final String MEMBER_NOT_FOUND =
		"{\"status\":404,\"code\":\"MEMBER_NOT_FOUND\",\"message\":\"없음\",\"data\":null}";

	private MockRestServiceServer server;
	private HttpMemberRoleAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://member");
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new HttpMemberRoleAdapter(builder.build());
	}

	@Test
	@DisplayName("관리자 여부는 응답 data의 admin 값을 그대로 돌려준다")
	void admin() {
		server.expect(requestTo(ADMIN_URL)).andExpect(method(HttpMethod.GET)).andRespond(ok("{\"admin\": true}"));
		server.expect(requestTo(ADMIN_URL)).andRespond(ok("{\"admin\": false}"));

		assertThat(adapter.isAdmin(3L)).isTrue();
		assertThat(adapter.isAdmin(3L)).isFalse();
		server.verify();
	}

	@Test
	@DisplayName("판매자는 status가 APPROVED일 때만이다 (승인 대기, 신청 없음은 아니다)")
	void seller() {
		server.expect(requestTo(SELLER_URL)).andExpect(method(HttpMethod.GET))
			.andRespond(ok("{\"sellerId\": 7, \"status\": \"APPROVED\"}"));
		server.expect(requestTo(SELLER_URL)).andRespond(ok("{\"sellerId\": 7, \"status\": \"PENDING\"}"));
		server.expect(requestTo(SELLER_URL)).andRespond(ok("{\"sellerId\": null, \"status\": null}"));

		assertThat(adapter.isApprovedSeller(3L)).isTrue();
		assertThat(adapter.isApprovedSeller(3L)).isFalse();
		assertThat(adapter.isApprovedSeller(3L)).isFalse();
		server.verify();
	}

	@Test
	@DisplayName("없거나 탈퇴한 회원(404 MEMBER_NOT_FOUND)은 역할이 없다")
	void memberNotFound() {
		server.expect(requestTo(ADMIN_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
			.contentType(MediaType.APPLICATION_JSON).body(MEMBER_NOT_FOUND));
		server.expect(requestTo(SELLER_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
			.contentType(MediaType.APPLICATION_JSON).body(MEMBER_NOT_FOUND));

		assertThat(adapter.isAdmin(3L)).isFalse();
		assertThat(adapter.isApprovedSeller(3L)).isFalse();
	}

	@Test
	@DisplayName("5xx·코드가 다른 404·값이 없는 응답은 역할 없음으로 보지 않고 503으로 던진다")
	void unknownIsUnavailable() {
		server.expect(requestTo(ADMIN_URL)).andRespond(withServerError());
		server.expect(requestTo(ADMIN_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
		server.expect(requestTo(ADMIN_URL)).andRespond(ok("null"));
		server.expect(requestTo(ADMIN_URL)).andRespond(ok("{}"));
		server.expect(requestTo(SELLER_URL)).andRespond(withServerError());
		server.expect(requestTo(SELLER_URL)).andRespond(ok("null"));

		for (int i = 0; i < 4; i++) {
			assertThatThrownBy(() -> adapter.isAdmin(3L)).isInstanceOf(AiExternalServiceUnavailableException.class);
		}
		for (int i = 0; i < 2; i++) {
			assertThatThrownBy(() -> adapter.isApprovedSeller(3L))
				.isInstanceOf(AiExternalServiceUnavailableException.class);
		}
	}

	private static ResponseCreator ok(String data) {
		return withSuccess("{\"status\":200,\"message\":\"조회\",\"data\":" + data + "}", MediaType.APPLICATION_JSON);
	}
}
