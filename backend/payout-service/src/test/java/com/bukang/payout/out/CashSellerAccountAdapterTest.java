package com.bukang.payout.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 캐시 서비스에 판매자 정산 계좌 등록 여부를 묻는 어댑터 (PRO-224)
 * 캐시 서비스에 아직 API가 없어 가정한 계약(GET /internal/v1/cash/sellers/{sellerId}/payout-account)으로 확인한다
 * 빈으로 등록하지 않는 클래스라 직접 만들어 MockRestServiceServer에 묶는다
 */
class CashSellerAccountAdapterTest {
	private static final String BASE_URL = "http://cash-service";
	private static final String URL = BASE_URL + "/internal/v1/cash/sellers/7001/payout-account";

	private MockRestServiceServer server;
	private CashSellerAccountAdapter adapter;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		adapter = new CashSellerAccountAdapter(builder.build());
	}

	@Test
	@DisplayName("계좌를 등록한 판매자면 '있음'(true)이다")
	void registered() {
		server.expect(requestTo(URL))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("""
				{ "status": 200, "message": "정산 계좌를 조회했습니다.", "data": { "registered": true } }
				""", MediaType.APPLICATION_JSON));

		assertThat(adapter.hasPayoutAccount(7001)).isTrue();
		server.verify();
	}

	@Test
	@DisplayName("계좌를 등록하지 않은 판매자면 '없음'(false)이다 (정산 내역을 '정산 계좌 미등록'으로 보류한다)")
	void notRegistered() {
		server.expect(requestTo(URL))
			.andRespond(withSuccess("""
				{ "status": 200, "message": "정산 계좌를 조회했습니다.", "data": { "registered": false } }
				""", MediaType.APPLICATION_JSON));

		assertThat(adapter.hasPayoutAccount(7001)).isFalse();
	}

	@Test
	@DisplayName("등록 여부 값이 없으면 '있음'이나 '없음'으로 정하지 않고 예외를 던진다")
	void throwsOnMissingValue() {
		server.expect(requestTo(URL))
			.andRespond(withSuccess("""
				{ "status": 200, "message": "정산 계좌를 조회했습니다.", "data": {} }
				""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> adapter.hasPayoutAccount(7001))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("sellerId=7001");
	}

	@Test
	@DisplayName("캐시 서비스가 오류로 응답하면 예외를 던진다 (배치는 그 정산 내역을 실패로 남긴다)")
	void throwsOnServerError() {
		server.expect(requestTo(URL)).andRespond(withServerError());

		assertThatThrownBy(() -> adapter.hasPayoutAccount(7001)).isInstanceOf(RestClientException.class);
	}
}
