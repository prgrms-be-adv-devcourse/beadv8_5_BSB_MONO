package com.bukang.payout.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bukang.payout.domain.PayoutStage;

/**
 * 캐시 서비스에 정산금 이동을 요청하는 어댑터 (PRO-252)
 * 캐시 서비스 API가 아직 없어 가정한 계약(POST /internal/v1/cash/payouts/{payoutId})으로 확인한다
 * 핵심은 결과를 둘로 나누는 것이다: 4xx는 돈이 안 나간 것이 확실하고(거절), 타임아웃 · 5xx는 모른다
 */
class CashPayoutTransferAdapterTest {
	private static final String BASE_URL = "http://cash-service";
	private static final String URL = BASE_URL + "/internal/v1/cash/payouts/31";
	private static final PayoutTransfer TRANSFER = new PayoutTransfer(31, 7, 47_250L, 2_750L, PayoutStage.최종_정산);

	private MockRestServiceServer server;
	private CashPayoutTransferAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new CashPayoutTransferAdapter(builder.build());
	}

	@Test
	@DisplayName("정산 ID를 경로에, 판매자 ID · 지급액 · 수수료 · 단계를 본문에 담아 보내고 2xx면 성공이다")
	void success() {
		server.expect(requestTo(URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().json("""
				{ "sellerId": 7, "payoutStage": "FINAL", "settlementAmount": 47250, "feeAmount": 2750 }
				""", true))
			.andRespond(withSuccess("""
				{ "status": 200, "message": "정산금을 옮겼습니다.", "data": null }
				""", MediaType.APPLICATION_JSON));

		assertThatCode(() -> adapter.transfer(TRANSFER)).doesNotThrowAnyException();
		server.verify();
	}

	@Test
	@DisplayName("지갑이 없어 404면 돈이 옮겨지지 않은 것이 확실하므로 거절로 본다")
	void notFoundIsRejected() {
		server.expect(requestTo(URL))
			.andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body("""
				{ "status": 404, "message": "존재하지 않는 지갑입니다.", "data": null }
				"""));

		assertThatThrownBy(() -> adapter.transfer(TRANSFER))
			.isInstanceOf(PayoutTransferRejectedException.class)
			.hasMessageContaining("payoutId=31")
			.hasMessageContaining("존재하지 않는 지갑입니다.");
	}

	@Test
	@DisplayName("409처럼 다른 4xx도 거절로 본다")
	void conflictIsRejected() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.CONFLICT));

		assertThatThrownBy(() -> adapter.transfer(TRANSFER)).isInstanceOf(PayoutTransferRejectedException.class);
	}

	@Test
	@DisplayName("5xx는 캐시 서비스가 옮긴 뒤 실패했을 수도 있으므로 결과를 모르는 것으로 본다")
	void serverErrorIsUnknown() {
		server.expect(requestTo(URL)).andRespond(withServerError());

		assertThatThrownBy(() -> adapter.transfer(TRANSFER)).isInstanceOf(PayoutTransferUnknownException.class);
	}

	@Test
	@DisplayName("응답을 기다리다 시간이 지나면 결과를 모르는 것으로 본다")
	void timeoutIsUnknown() {
		server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

		assertThatThrownBy(() -> adapter.transfer(TRANSFER))
			.isInstanceOf(PayoutTransferUnknownException.class)
			.hasMessageContaining("payoutId=31");
	}

	@Test
	@DisplayName("오류 본문이 길면 잘라서 메시지에 남긴다")
	void abbreviatesLongBody() {
		String body = "가".repeat(500);

		assertThat(CashPayoutTransferAdapter.abbreviate(body))
			.hasSize(300 + "…(500자)".length())
			.endsWith("…(500자)");
	}
}
