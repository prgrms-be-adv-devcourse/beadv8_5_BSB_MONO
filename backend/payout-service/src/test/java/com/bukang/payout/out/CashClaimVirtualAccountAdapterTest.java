package com.bukang.payout.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
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
import org.springframework.web.client.RestClientException;

/**
 * 캐시 서비스의 청구 가상계좌 발급 내부 API(BUK-145)를 부르는 어댑터 (BUK-88)
 */
class CashClaimVirtualAccountAdapterTest {
	private static final String BASE_URL = "http://cash-service";
	private static final String URL = BASE_URL + "/internal/v1/cash/claims/RC-20261214-0A3F/virtual-account";

	private MockRestServiceServer server;
	private CashClaimVirtualAccountAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new CashClaimVirtualAccountAdapter(builder.build());
	}

	@Test
	@DisplayName("청구 ID를 경로에, 청구 금액을 본문에 담아 보내고 응답의 은행 이름·계좌번호를 돌려준다")
	void issue() {
		server.expect(requestTo(URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().json("""
				{ "amount": 105500 }
				""", true))
			.andRespond(withSuccess("""
				{ "status": 200, "message": "가상계좌를 발급했습니다.",
				  "data": { "bankName": "국민은행", "accountNumber": "X6000123456789" } }
				""", MediaType.APPLICATION_JSON));

		assertThat(adapter.issue("RC-20261214-0A3F", 105_500L, LocalDate.of(2027, 1, 13)))
			.contains(new ClaimVirtualAccount("국민은행", "X6000123456789"));
		server.verify();
	}

	@Test
	@DisplayName("캐시가 503으로 답하면 예외를 던져 청구 배치가 다음 날 다시 요청하게 한다")
	void serverErrorThrows() {
		server.expect(requestTo(URL))
			.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON).body("""
				{ "status": 503, "message": "가상계좌 발급에 실패했습니다.", "data": null }
				"""));

		assertThatThrownBy(() -> adapter.issue("RC-20261214-0A3F", 105_500L, LocalDate.of(2027, 1, 13)))
			.isInstanceOf(RestClientException.class);
	}
}
