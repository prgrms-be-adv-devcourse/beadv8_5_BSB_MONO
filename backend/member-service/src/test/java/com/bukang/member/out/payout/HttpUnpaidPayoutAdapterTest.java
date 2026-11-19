package com.bukang.member.out.payout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bukang.member.domain.exception.WithdrawCheckFailedException;

// 정산 서비스 「지급 안 된 정산금 확인」 내부 API 클라이언트 (CRU-23). 주소에는 판매자 ID(Seller.id)를 넣는다
class HttpUnpaidPayoutAdapterTest {
	private static final String BASE_URL = "http://payout";
	private static final String URL = BASE_URL + "/internal/v1/payout/sellers/3/unpaid";

	private MockRestServiceServer server;
	private HttpUnpaidPayoutAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new HttpUnpaidPayoutAdapter(builder.build());
	}

	@Test
	@DisplayName("판매자 ID로 묻고 hasUnpaid 값을 그대로 돌려준다 (모르는 필드는 무시)")
	void hasUnpaidPayout() {
		server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("""
				{ "status": 200, "message": "조회", "data": { "hasUnpaid": true, "amount": 1000 } }
				""", MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withSuccess("""
			{ "status": 200, "message": "조회", "data": { "hasUnpaid": false } }
			""", MediaType.APPLICATION_JSON));

		assertThat(adapter.hasUnpaidPayout(3L)).isTrue();
		assertThat(adapter.hasUnpaidPayout(3L)).isFalse();
		server.verify();
	}

	@Test
	@DisplayName("연결 실패·시간 초과·4xx·5xx·data 없음·값 없음은 503 예외다 (탈퇴를 막는 쪽으로 실패)")
	void unavailable() {
		server.expect(requestTo(URL)).andRespond(withException(new ConnectException("connection refused")));
		server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("read timed out")));
		server.expect(requestTo(URL)).andRespond(withBadRequest());
		server.expect(requestTo(URL)).andRespond(withServerError());
		server.expect(requestTo(URL)).andRespond(withSuccess("""
			{ "status": 200, "message": "조회", "data": null }
			""", MediaType.APPLICATION_JSON));
		server.expect(requestTo(URL)).andRespond(withSuccess("""
			{ "status": 200, "message": "조회", "data": {} }
			""", MediaType.APPLICATION_JSON));

		for (int attempt = 0; attempt < 6; attempt++) {
			assertThatThrownBy(() -> adapter.hasUnpaidPayout(3L))
				.isInstanceOf(WithdrawCheckFailedException.class);
		}
		server.verify();
	}
}
