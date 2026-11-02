package com.bukang.cash.out.pg;

import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.bukang.cash.domain.exception.PaymentApprovalFailedException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Toss페이먼츠 결제 승인·조회 API를 부른다 (POST /v1/payments/confirm, GET /v1/payments/orders/{orderId}, PRO-81, PRO-200)
 * 시크릿 키를 Basic 인증으로 보낸다. 등록은 PaymentClientConfig가 한다 (cash.clients.pg.mode=http)
 */
@Slf4j
@RequiredArgsConstructor
public class TossPaymentClient implements PaymentClient {
	static final String CONFIRM_PATH = "/v1/payments/confirm";
	static final String ORDER_LOOKUP_PATH = "/v1/payments/orders/{orderId}";
	private static final String STATUS_DONE = "DONE";

	private final RestClient restClient;
	private final String secretKey;

	@Override
	public PaymentApprovalResult approve(String paymentKey, String orderId, long amount) {
		PaymentResponse response;
		try {
			response = restClient.post()
				.uri(CONFIRM_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.headers(headers -> headers.setBasicAuth(secretKey, ""))
				.body(new ConfirmRequest(paymentKey, orderId, amount))
				.retrieve()
				.body(PaymentResponse.class);
		} catch (RestClientResponseException exception) {
			// Toss는 거절 시 {code, message} 형태로 응답한다
			String pgCode = readErrorCode(exception);
			log.error("Toss 결제 승인이 거절됐습니다. paymentKey={}, orderId={}, status={}, code={}, body={}",
				paymentKey, orderId, exception.getStatusCode(), pgCode, exception.getResponseBodyAsString());
			throw new PaymentApprovalFailedException("PG사가 결제 승인을 거절했습니다.", pgCode);
		} catch (RestClientException exception) {
			log.error("Toss 결제 승인 서버에 연결하지 못했습니다. paymentKey={}, orderId={}", paymentKey, orderId, exception);
			throw new PaymentApprovalFailedException("PG 서버에 연결하지 못했습니다. 잠시 후 다시 시도해주세요.");
		}
		if (response == null) {
			log.error("Toss 결제 승인 응답이 비어 있습니다. paymentKey={}, orderId={}", paymentKey, orderId);
			throw new PaymentApprovalFailedException("PG 서버 응답이 올바르지 않습니다.");
		}
		return new PaymentApprovalResult(response.totalAmount(), response.paymentKey());
	}

	@Override
	public Optional<PaymentApprovalResult> findCompletedByOrderId(String orderId) {
		PaymentResponse response;
		try {
			response = restClient.get()
				.uri(ORDER_LOOKUP_PATH, orderId)
				.accept(MediaType.APPLICATION_JSON)
				.headers(headers -> headers.setBasicAuth(secretKey, ""))
				.retrieve()
				.body(PaymentResponse.class);
		} catch (RestClientException exception) {
			// 조회 자체가 실패하면 승인 여부를 확인할 수 없다는 뜻이므로, 안전하게 "아직 안 끝남"으로 본다
			log.error("Toss 결제 조회에 실패했습니다. orderId={}", orderId, exception);
			return Optional.empty();
		}
		if (response == null || !STATUS_DONE.equals(response.status())) {
			return Optional.empty();
		}
		return Optional.of(new PaymentApprovalResult(response.totalAmount(), response.paymentKey()));
	}

	private String readErrorCode(RestClientResponseException exception) {
		try {
			ErrorResponse error = exception.getResponseBodyAs(ErrorResponse.class);
			return error == null ? null : error.code();
		} catch (RuntimeException parseException) {
			log.warn("Toss 오류 응답 본문을 파싱하지 못했습니다. body={}", exception.getResponseBodyAsString());
			return null;
		}
	}

	private record ConfirmRequest(String paymentKey, String orderId, long amount) {
	}

	// Toss 응답(Payment 객체)엔 필드가 훨씬 많지만, 지갑에 반영할 때 쓰는 값만 받는다. status는 조회 응답에서만 쓴다
	@JsonIgnoreProperties(ignoreUnknown = true)
	private record PaymentResponse(String paymentKey, long totalAmount, String status) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record ErrorResponse(String code, String message) {
	}
}
