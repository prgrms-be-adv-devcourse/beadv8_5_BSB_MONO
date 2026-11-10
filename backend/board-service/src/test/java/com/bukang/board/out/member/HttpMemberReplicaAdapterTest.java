package com.bukang.board.out.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.ServerSocket;
import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 회원 서비스 내부 API를 부르는 어댑터. 응답 모양은 member-service의 InternalV1MemberReplicasTest가 확인하는 것과 같은 JSON이다
 * (필드 이름이 어긋나면 이 테스트나 저쪽 테스트가 먼저 깨진다)
 */
class HttpMemberReplicaAdapterTest {
	private static final String BASE_URL = "http://member";
	private static final String URL = BASE_URL + "/internal/v1/member/members/replicas?afterId=7&size=500";
	private static final String RESPONSE = """
		{
			"status": 200,
			"message": "회원 복사본 목록을 조회했습니다.",
			"data": {
				"members": [
					{ "memberId": 8, "nickname": "러너A", "sellerApproved": true, "withdrawn": false,
						"createDate": "2026-10-01T10:00:00", "modifyDate": "2026-11-10T09:30:00", "email": "ignored@crewrun.com" },
					{ "memberId": 9, "nickname": "탈퇴한 회원", "sellerApproved": false, "withdrawn": true,
						"createDate": "2026-09-01T10:00:00.5", "modifyDate": "2026-11-09T18:00:00.123456" }
				],
				"hasNext": true,
				"nextAfterId": 9
			}
		}
		""";

	private MockRestServiceServer server;
	private HttpMemberReplicaAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new HttpMemberReplicaAdapter(builder.build());
	}

	@Test
	@DisplayName("afterId와 size로 GET 하고, 응답의 회원 여섯 칸과 hasNext, nextAfterId를 읽는다 (모르는 칸은 무시)")
	void fetch() {
		server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

		MemberReplicaPort.MemberReplicaPage page = adapter.fetch(7, 500);

		assertThat(page.hasNext()).isTrue();
		assertThat(page.nextAfterId()).isEqualTo(9);
		assertThat(page.members()).containsExactly(
			new MemberReplicaPort.MemberReplica(8, "러너A", true, false, LocalDateTime.of(2026, 10, 1, 10, 0),
				LocalDateTime.of(2026, 11, 10, 9, 30)),
			new MemberReplicaPort.MemberReplica(9, "탈퇴한 회원", false, true, LocalDateTime.of(2026, 9, 1, 10, 0, 0, 500000000),
				LocalDateTime.of(2026, 11, 9, 18, 0, 0, 123456000)));
		server.verify();
	}

	@Test
	@DisplayName("회원 서비스가 4xx·5xx로 답하면 예외로 알린다")
	void errorStatus() {
		server.expect(requestTo(URL)).andRespond(withServerError());
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

		assertThatThrownBy(() -> adapter.fetch(7, 500)).isInstanceOf(RestClientException.class);
		assertThatThrownBy(() -> adapter.fetch(7, 500)).isInstanceOf(RestClientException.class);
	}

	@Test
	@DisplayName("응답에 data나 members가 없으면 빈 목록으로 착각하지 않고 예외로 알린다")
	void missingData() {
		server.expect(requestTo(URL))
			.andRespond(withSuccess("{ \"status\": 200, \"message\": \"조회\", \"data\": null }",
				MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL))
			.andRespond(withSuccess("{ \"status\": 200, \"message\": \"조회\", \"data\": {} }",
				MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> adapter.fetch(7, 500)).isInstanceOf(IllegalStateException.class);
		// 빈 data는 읽는 단계에서 이미 실패할 수 있다. 어느 쪽이든 빈 쪽으로 넘어가지 않는 것이 약속이다
		assertThatThrownBy(() -> adapter.fetch(7, 500)).isInstanceOf(RuntimeException.class);
	}

	@Test
	@DisplayName("회원 서비스가 응답하지 않으면 제한 시간 안에 예외로 끝난다 (게시판 기동을 오래 붙잡지 않음)")
	void timesOut() throws Exception {
		// 연결은 받지만 응답은 주지 않는 서버
		try (ServerSocket silent = new ServerSocket(0)) {
			RestClient client = MemberClientConfig.restClient("http://localhost:" + silent.getLocalPort(),
				Duration.ofMillis(300));
			HttpMemberReplicaAdapter slowAdapter = new HttpMemberReplicaAdapter(client);

			long start = System.nanoTime();
			assertThatThrownBy(() -> slowAdapter.fetch(0, 500)).isInstanceOf(RestClientException.class);

			assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
		}
	}
}
