package com.bukang.inquiry.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bukang.inquiry.domain.exception.InquiryExternalServiceUnavailableException;
import com.bukang.inquiry.out.member.HttpMemberNicknamesAdapter;

/**
 * 회원 서비스 내부 API(GET /internal/v1/member/members/nicknames?ids=1,2 → RsData의 data [{memberId, nickname}], 최대 100개,
 * 없는 회원은 빠짐)와 계약이 맞는지 확인한다
 */
class HttpMemberNicknamesAdapterTest {
	private static final String URL = "http://member/internal/v1/member/members/nicknames?ids=";

	private MockRestServiceServer server;
	private HttpMemberNicknamesAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://member");
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new HttpMemberNicknamesAdapter(builder.build());
	}

	@Test
	@DisplayName("회원 ID를 쉼표로 이어 묻고, 응답에 없는 회원은 결과에서 빠진다")
	void nicknames() {
		server.expect(requestTo(URL + "1%2C2")).andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(body("[{\"memberId\": 1, \"nickname\": \"러너하나\"}]"), MediaType.APPLICATION_JSON));

		assertThat(adapter.findNicknames(List.of(1L, 2L, 1L))).containsOnly(java.util.Map.entry(1L, "러너하나"));
		server.verify();
	}

	@Test
	@DisplayName("101명 이상은 100명씩 나눠 묻는다")
	void chunks() {
		List<Long> ids = LongStream.rangeClosed(1, 101).boxed().toList();
		server.expect(requestTo(URL + ids.subList(0, 100).stream().map(String::valueOf)
			.collect(java.util.stream.Collectors.joining("%2C")))).andRespond(withSuccess(body("[]"),
			MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL + "101")).andRespond(
			withSuccess(body("[{\"memberId\": 101, \"nickname\": \"백한번째\"}]"), MediaType.APPLICATION_JSON));

		assertThat(adapter.findNicknames(ids)).containsOnly(java.util.Map.entry(101L, "백한번째"));
		server.verify();
	}

	@Test
	@DisplayName("5xx·값이 없는 응답은 503으로 던진다 (부르는 쪽이 닉네임만 비운다)")
	void unavailable() {
		server.expect(requestTo(URL + "1")).andRespond(withServerError());
		server.expect(requestTo(URL + "1")).andRespond(withSuccess(body("null"), MediaType.APPLICATION_JSON));

		for (int i = 0; i < 2; i++) {
			assertThatThrownBy(() -> adapter.findNicknames(List.of(1L)))
				.isInstanceOf(InquiryExternalServiceUnavailableException.class);
		}
	}

	private static String body(String data) {
		return "{\"status\":200,\"message\":\"조회\",\"data\":" + data + "}";
	}
}
