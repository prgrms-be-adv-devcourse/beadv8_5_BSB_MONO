package com.bukang.cash.out.pg;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.bukang.cash.domain.exception.recovery.VirtualAccountIssueFailedException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Toss페이먼츠 가상계좌 발급 API를 부르고(POST /v1/virtual-accounts), 주문번호로 이미 발급한 계좌를 찾는다
 * (GET /v1/payments/orders/{orderId}). 판매자가 갚을 돈(청구)마다 계좌 하나를 발급한다.
 * 시크릿 키를 Basic 인증으로 보낸다. TossPaymentClient가 감싸 쓴다. 발급과 조회는 응답 시간 제한이 달라 RestClient를 따로 받는다
 */
@Slf4j
@RequiredArgsConstructor
class TossVirtualAccountClient {
	static final String ISSUE_PATH = "/v1/virtual-accounts";
	// 은행은 국민은행 한 곳으로 정한다
	static final String BANK_KOOKMIN = "06";
	static final String BANK_KOOKMIN_NAME = "국민은행";
	// 유효기간은 Toss 최대인 90일이다. 정산의 입금 기한(30일)을 넘겨 늦게 내도 자동으로 기록된다
	static final int VALID_HOURS = 90 * 24;
	private static final String ORDER_NAME = "크루런 정산금 반환";
	private static final String CUSTOMER_NAME = "크루런 판매자";
	private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

	private final RestClient issueRestClient;
	private final RestClient lookupRestClient;
	private final String secretKey;

	VirtualAccountIssueResult issue(String orderId, long amount) {
		AccountResponse response;
		try {
			response = issueRestClient.post()
				.uri(ISSUE_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.headers(headers -> headers.setBasicAuth(secretKey, ""))
				.body(new IssueRequest(amount, orderId, ORDER_NAME, CUSTOMER_NAME, BANK_KOOKMIN, VALID_HOURS))
				.retrieve()
				.body(AccountResponse.class);
		} catch (RestClientResponseException exception) {
			log.error("Toss 가상계좌 발급이 거절됐습니다. orderId={}, status={}, code={}, body={}",
				orderId, exception.getStatusCode(), TossErrorCodeReader.readCode(exception),
				exception.getResponseBodyAsString());
			throw new VirtualAccountIssueFailedException("PG사가 가상계좌 발급을 거절했습니다.");
		} catch (RestClientException exception) {
			log.error("Toss 가상계좌 발급 서버에 연결하지 못했습니다. orderId={}", orderId, exception);
			throw new VirtualAccountIssueFailedException("PG 서버에 연결하지 못했습니다. 잠시 후 다시 시도해주세요.");
		}
		return toResult(orderId, response);
	}

	// 404면 이 주문번호로 발급한 계좌가 없다. 조회가 실패하면 발급됐는지 모르므로 새로 발급하지 않도록 예외로 알린다
	Optional<VirtualAccountIssueResult> find(String orderId) {
		AccountResponse response;
		try {
			response = lookupRestClient.get()
				.uri(TossPaymentConfirmClient.ORDER_LOOKUP_PATH, orderId)
				.accept(MediaType.APPLICATION_JSON)
				.headers(headers -> headers.setBasicAuth(secretKey, ""))
				.retrieve()
				.body(AccountResponse.class);
		} catch (RestClientResponseException exception) {
			if (exception.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
				return Optional.empty();
			}
			log.error("Toss 가상계좌 조회가 거절됐습니다. orderId={}, status={}, body={}",
				orderId, exception.getStatusCode(), exception.getResponseBodyAsString());
			throw new VirtualAccountIssueFailedException("PG사에서 가상계좌를 확인하지 못했습니다.");
		} catch (RestClientException exception) {
			log.error("Toss 가상계좌 조회 서버에 연결하지 못했습니다. orderId={}", orderId, exception);
			throw new VirtualAccountIssueFailedException("PG 서버에 연결하지 못했습니다. 잠시 후 다시 시도해주세요.");
		}
		return Optional.of(toResult(orderId, response));
	}

	// 비밀값이 없으면 입금 알림을 확인할 수 없으므로 계좌 정보와 함께 모두 있어야 한다
	private VirtualAccountIssueResult toResult(String orderId, AccountResponse response) {
		VirtualAccount account = response == null ? null : response.virtualAccount();
		if (account == null || account.accountNumber() == null || account.dueDate() == null
			|| response.secret() == null) {
			log.error("Toss 응답에 가상계좌 정보가 없습니다. orderId={}", orderId);
			throw new VirtualAccountIssueFailedException("PG 서버 응답이 올바르지 않습니다.");
		}
		return new VirtualAccountIssueResult(BANK_KOOKMIN_NAME, account.accountNumber(),
			account.dueDate().atZoneSameInstant(KOREA).toLocalDateTime(), response.secret());
	}

	private record IssueRequest(
		long amount, String orderId, String orderName, String customerName, String bank, int validHours
	) {
	}

	// Toss 응답(Payment 객체) 중 계좌번호·입금 기한과 웹훅 비밀값만 받는다. 발급과 조회 응답이 같은 모양이다
	@JsonIgnoreProperties(ignoreUnknown = true)
	private record AccountResponse(String secret, VirtualAccount virtualAccount) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record VirtualAccount(String accountNumber, OffsetDateTime dueDate) {
	}
}
