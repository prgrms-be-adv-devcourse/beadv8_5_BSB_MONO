package com.bukang.cash.out.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bukang.cash.domain.exception.admin.MemberServiceUnavailableException;

class HttpMemberAdminAdapterTest {
	private static final String BASE_URL = "http://member";
	private static final long MEMBER_ID = 3L;
	private static final String URL = BASE_URL + "/internal/v1/member/members/3/admin";
	private static final String NULL_DATA = "{ \"status\": 200, \"message\": \"조회\", \"data\": null }";
	private static final String EMPTY_DATA = "{ \"status\": 200, \"message\": \"조회\", \"data\": {} }";

	private MockRestServiceServer server;
	private HttpMemberAdminAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new HttpMemberAdminAdapter(builder.build());
	}

	@Test
	@DisplayName("회원 서비스에 회원 ID로 묻고, 응답의 admin 값을 돌려준다")
	void admin() {
		server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(response(true), MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL))
			.andRespond(withSuccess(response(false), MediaType.APPLICATION_JSON));

		assertThat(adapter.isAdmin(MEMBER_ID)).isTrue();
		assertThat(adapter.isAdmin(MEMBER_ID)).isFalse();
		server.verify();
	}

	@Test
	@DisplayName("없거나 탈퇴한 회원(404 MEMBER_NOT_FOUND)은 관리자가 아니다")
	void notFound() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
			.contentType(MediaType.APPLICATION_JSON)
			.body("{ \"status\": 404, \"code\": \"MEMBER_NOT_FOUND\", \"message\": \"존재하지 않는 회원입니다.\" }"));

		assertThat(adapter.isAdmin(MEMBER_ID)).isFalse();
	}

	@Test
	@DisplayName("회원 없음이 아닌 404(경로가 어긋남)는 관리자 아님으로 보지 않고 503 예외로 알린다")
	void notFoundWithoutMemberCode() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
			.contentType(MediaType.APPLICATION_JSON)
			.body("{ \"timestamp\": \"2026-10-30T10:00:00\", \"status\": 404, \"error\": \"Not Found\" }"));
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> adapter.isAdmin(MEMBER_ID)).isInstanceOf(MemberServiceUnavailableException.class);
		assertThatThrownBy(() -> adapter.isAdmin(MEMBER_ID)).isInstanceOf(MemberServiceUnavailableException.class);
	}

	@Test
	@DisplayName("회원 서비스가 5xx·그 밖의 4xx로 답하거나 응답이 늦으면 관리자로 보지 않고 503 예외로 알린다")
	void unavailable() {
		server.expect(requestTo(URL)).andRespond(withServerError());
		server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("read timed out")));
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

		for (int i = 0; i < 3; i++) {
			assertThatThrownBy(() -> adapter.isAdmin(MEMBER_ID))
				.isInstanceOf(MemberServiceUnavailableException.class)
				.hasMessage(MemberServiceUnavailableException.MESSAGE);
		}
	}

	@Test
	@DisplayName("응답에 admin 값이 없으면 관리자인지 알 수 없으므로 503 예외로 알린다")
	void missingValue() {
		server.expect(requestTo(URL)).andRespond(withSuccess(NULL_DATA, MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withSuccess(EMPTY_DATA, MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> adapter.isAdmin(MEMBER_ID)).isInstanceOf(MemberServiceUnavailableException.class);
		assertThatThrownBy(() -> adapter.isAdmin(MEMBER_ID)).isInstanceOf(MemberServiceUnavailableException.class);
	}

	@Test
	@DisplayName("회원 서비스가 시간 제한 안에 답하지 않으면 기다리지 않고 503 예외로 알린다 (실제 연결로 확인)")
	void timeout() throws Exception {
		// 연결은 받지만 아무 응답도 하지 않는 서버
		try (ServerSocket silentServer = new ServerSocket(0)) {
			HttpMemberAdminAdapter slowAdapter = new MemberClientConfig().httpMemberAdminAdapter(
				"http://localhost:" + silentServer.getLocalPort(), Duration.ofMillis(300));
			long startedAt = System.nanoTime();

			assertThatThrownBy(() -> slowAdapter.isAdmin(MEMBER_ID))
				.isInstanceOf(MemberServiceUnavailableException.class);
			assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(2));
		}
	}

	// 회원 서비스 InternalV1MemberController가 실제로 돌려주는 모양 (MemberInternalApiExamples.ADMIN)
	private String response(boolean admin) {
		return """
			{ "status": 200, "message": "관리자 여부를 조회했습니다.", "data": { "admin": %s } }
			""".formatted(admin);
	}
}
