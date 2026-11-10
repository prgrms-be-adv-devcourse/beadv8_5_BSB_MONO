package com.bukang.cash.out.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

class HttpBankTransferClientTest {
	private static final String BASE_URL = "http://bank";

	private MockRestServiceServer server;
	private HttpBankTransferClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		client = new HttpBankTransferClient(builder.build());
	}

	static Stream<Arguments> transferResponses() {
		return Stream.of(
			Arguments.of("완료", withSuccess(body("COMPLETED"), MediaType.APPLICATION_JSON),
				BankTransferResult.COMPLETED),
			Arguments.of("실패", withSuccess(body("FAILED"), MediaType.APPLICATION_JSON), BankTransferResult.FAILED),
			Arguments.of("처리 중", withSuccess(body("PROCESSING"), MediaType.APPLICATION_JSON),
				BankTransferResult.UNKNOWN),
			Arguments.of("4xx 거절", withStatus(HttpStatus.BAD_REQUEST), BankTransferResult.FAILED),
			Arguments.of("5xx", withStatus(HttpStatus.GATEWAY_TIMEOUT), BankTransferResult.UNKNOWN),
			Arguments.of("응답 시간 초과", withException(new SocketTimeoutException()), BankTransferResult.UNKNOWN)
		);
	}

	@ParameterizedTest(name = "{0} → {2}")
	@MethodSource("transferResponses")
	@DisplayName("송금 ID·판매자·금액을 보내고, 은행 응답을 완료·실패·결과 모름으로 나눈다")
	void transfer(String name, ResponseCreator response, BankTransferResult expected) {
		server.expect(requestTo(BASE_URL + "/fakebank/v1/transfers"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.transferId").value("payout-transfer-11"))
			.andExpect(jsonPath("$.sellerId").value(7))
			.andExpect(jsonPath("$.amount").value(30_000))
			.andRespond(response);

		BankTransferResult result = client.transfer("payout-transfer-11", 7, 30_000);

		assertThat(result).isEqualTo(expected);
		server.verify();
	}

	private static String body(String status) {
		return """
			{ "transferId": "payout-transfer-11", "status": "%s" }
			""".formatted(status);
	}
}
