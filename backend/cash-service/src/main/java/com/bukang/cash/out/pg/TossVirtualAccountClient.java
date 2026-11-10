package com.bukang.cash.out.pg;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;

/**
 * Toss페이먼츠 가상계좌 발급 API를 부른다 (POST /v1/virtual-accounts)
 * 판매자가 갚을 돈(청구)마다 계좌 하나를 발급한다. 시크릿 키를 Basic 인증으로 보낸다. TossPaymentClient가 감싸 쓴다
 */
@RequiredArgsConstructor
class TossVirtualAccountClient {
	static final String ISSUE_PATH = "/v1/virtual-accounts";
	// 은행은 국민은행 한 곳으로 정한다
	static final String BANK_KOOKMIN = "06";
	// 유효기간은 Toss 최대인 90일이다. 정산의 입금 기한(30일)을 넘겨 늦게 내도 자동으로 기록된다
	static final int VALID_HOURS = 90 * 24;
	private static final String ORDER_NAME = "크루런 정산금 반환";
	private static final String CUSTOMER_NAME = "크루런 판매자";
	private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

	private final RestClient restClient;
	private final String secretKey;

	VirtualAccountIssueResult issue(String orderId, long amount) {
		IssueResponse response = restClient.post()
			.uri(ISSUE_PATH)
			.contentType(MediaType.APPLICATION_JSON)
			.accept(MediaType.APPLICATION_JSON)
			.headers(headers -> headers.setBasicAuth(secretKey, ""))
			.body(new IssueRequest(amount, orderId, ORDER_NAME, CUSTOMER_NAME, BANK_KOOKMIN, VALID_HOURS))
			.retrieve()
			.body(IssueResponse.class);

		VirtualAccount account = response.virtualAccount();
		return new VirtualAccountIssueResult(
			account.accountNumber(), account.dueDate().atZoneSameInstant(KOREA).toLocalDateTime(), response.secret());
	}

	private record IssueRequest(
		long amount, String orderId, String orderName, String customerName, String bank, int validHours
	) {
	}

	// Toss 응답(Payment 객체) 중 계좌번호·입금 기한과 웹훅 비밀값만 받는다
	@JsonIgnoreProperties(ignoreUnknown = true)
	private record IssueResponse(String secret, VirtualAccount virtualAccount) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record VirtualAccount(String accountNumber, OffsetDateTime dueDate) {
	}
}
