package com.bukang.order.out.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bukang.order.domain.exception.ExternalServiceUnavailableException;

class HttpMemberNicknamesAdapterTest {
	private static final String BASE_URL = "http://member";
	private static final String URL = BASE_URL + "/internal/v1/member/members/nicknames?ids=";

	private MockRestServiceServer server;
	private HttpMemberNicknamesAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new HttpMemberNicknamesAdapter(builder.build());
	}

	@Test
	@DisplayName("회원 ID를 쉼표로 이어 묻고, 응답에 없는 회원은 결과에서 빠진다")
	void nicknames() {
		server.expect(requestTo(URL + "3%2C8")).andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(data("[{ \"memberId\": 3, \"nickname\": \"러너A\" }]"), MediaType.APPLICATION_JSON));

		assertThat(adapter.findNicknames(List.of(3L, 8L, 3L))).isEqualTo(Map.of(3L, "러너A"));
		server.verify();
	}

	@Test
	@DisplayName("한 번에 100명까지라 101명이면 두 번 나눠 묻는다")
	void chunks() {
		List<Long> ids = LongStream.rangeClosed(1, 101).boxed().toList();
		server.expect(requestTo(URL + ids.subList(0, 100).stream().map(String::valueOf)
				.collect(Collectors.joining("%2C"))))
			.andRespond(withSuccess(data("[{ \"memberId\": 1, \"nickname\": \"하나\" }]"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL + "101"))
			.andRespond(withSuccess(data("[{ \"memberId\": 101, \"nickname\": \"백하나\" }]"),
				MediaType.APPLICATION_JSON));

		assertThat(adapter.findNicknames(ids)).isEqualTo(Map.of(1L, "하나", 101L, "백하나"));
		server.verify();
	}

	@Test
	@DisplayName("닉네임이 빈 줄은 건너뛴다 (부르는 쪽이 「탈퇴한 회원」으로 보여 줌)")
	void skipsNullNickname() {
		server.expect(requestTo(URL + "3%2C8")).andRespond(withSuccess(
			data("[{ \"memberId\": 3, \"nickname\": null }, { \"memberId\": 8, \"nickname\": \"러너B\" }]"),
			MediaType.APPLICATION_JSON));

		assertThat(adapter.findNicknames(List.of(3L, 8L))).isEqualTo(Map.of(8L, "러너B"));
	}

	@Test
	@DisplayName("404(경로 어긋남)·5xx·data 없는 응답은 503 예외")
	void unavailable() {
		server.expect(requestTo(URL + "3")).andRespond(withStatus(HttpStatus.NOT_FOUND));
		server.expect(requestTo(URL + "3")).andRespond(withServerError());
		server.expect(requestTo(URL + "3"))
			.andRespond(withSuccess("{ \"status\": 200, \"message\": \"ok\", \"data\": null }",
				MediaType.APPLICATION_JSON));

		for (int i = 0; i < 3; i++) {
			assertThatThrownBy(() -> adapter.findNicknames(List.of(3L)))
				.isInstanceOf(ExternalServiceUnavailableException.class);
		}
	}

	private static String data(String json) {
		return "{ \"status\": 200, \"message\": \"ok\", \"data\": " + json + " }";
	}
}
