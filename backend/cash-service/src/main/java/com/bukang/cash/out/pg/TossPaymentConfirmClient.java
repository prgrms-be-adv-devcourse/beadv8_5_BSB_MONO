package com.bukang.cash.out.pg;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.bukang.cash.domain.exception.charge.PaymentApprovalFailedException;
import com.bukang.cash.domain.exception.charge.PaymentLookupFailedException;
import com.bukang.cash.out.pg.TossErrorCodeReader.ErrorResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Toss페이먼츠 결제 승인·조회 API를 부른다 (POST /v1/payments/confirm, GET /v1/payments/orders/{orderId})
 * 시크릿 키를 Basic 인증으로 보낸다. TossPaymentClient가 감싸 쓴다
 * 승인과 조회는 응답 시간 제한이 달라 RestClient를 따로 받는다
 */
@Slf4j
@RequiredArgsConstructor
class TossPaymentConfirmClient {
	static final String CONFIRM_PATH = "/v1/payments/confirm";
	static final String ORDER_LOOKUP_PATH = "/v1/payments/orders/{orderId}";
	private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
	static final String GENERIC_REJECTION_MESSAGE = "결제를 승인하지 못했습니다. 잠시 후 다시 시도해주세요.";

	private final RestClient moneyMovingRestClient;
	private final RestClient lookupRestClient;
	private final String secretKey;

	PaymentApprovalResult approve(String paymentKey, String orderId, long amount) {
		PaymentResponse response;
		try {
			response = moneyMovingRestClient.post()
				.uri(CONFIRM_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.headers(headers -> headers.setBasicAuth(secretKey, ""))
				.body(new ConfirmRequest(paymentKey, orderId, amount))
				.retrieve()
				.body(PaymentResponse.class);
		} catch (RestClientResponseException exception) {
			// Toss는 거절 시 {code, message} 형태로 응답한다
			ErrorResponse error = TossErrorCodeReader.read(exception);
			String pgCode = error == null ? null : error.code();
			log.error("Toss 결제 승인이 거절됐습니다. paymentKey={}, orderId={}, status={}, code={}, body={}",
				paymentKey, orderId, exception.getStatusCode(), pgCode, exception.getResponseBodyAsString());

			// 5xx는 Toss 내부 오류라, 코드를 못 읽은 응답은 무슨 뜻인지 몰라 승인 여부를 단정할 수 없다
			boolean resultUnknown = exception.getStatusCode().is5xxServerError()
				|| pgCode == null
				|| TossErrorCode.from(pgCode).isResultUnknown();

			throw new PaymentApprovalFailedException(userMessage(error), pgCode, resultUnknown);

		} catch (RestClientException exception) {
			log.error("Toss 결제 승인 서버에 연결하지 못했습니다. paymentKey={}, orderId={}", paymentKey, orderId, exception);
			throw new PaymentApprovalFailedException("PG 서버에 연결하지 못했습니다. 잠시 후 다시 시도해주세요.");
		}
		if (response == null) {
			log.error("Toss 결제 승인 응답이 비어 있습니다. paymentKey={}, orderId={}", paymentKey, orderId);
			throw new PaymentApprovalFailedException("PG 서버 응답이 올바르지 않습니다.");
		}
		return new PaymentApprovalResult(response.totalAmount(), response.paymentKey(), response.approvedAtInKorea());
	}

	PaymentLookupResult lookupByOrderId(String orderId) {
		PaymentResponse response;
		try {
			response = lookupRestClient.get()
				.uri(ORDER_LOOKUP_PATH, orderId)
				.accept(MediaType.APPLICATION_JSON)
				.headers(headers -> headers.setBasicAuth(secretKey, ""))
				.retrieve()
				.body(PaymentResponse.class);
		} catch (RestClientResponseException exception) {
			if (exception.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
				// Toss가 이 주문번호의 결제를 모른다 — 승인된 적이 없다
				return PaymentLookupResult.notCompleted();
			}
			log.error("Toss 결제 조회가 거절됐습니다. orderId={}, status={}, body={}",
				orderId, exception.getStatusCode(), exception.getResponseBodyAsString());
			throw new PaymentLookupFailedException("Toss 결제 조회가 거절됐습니다. orderId=" + orderId, exception);
		} catch (RestClientException exception) {
			// 조회 자체가 실패하면 승인 여부를 확인할 수 없다 — "아직 안 끝남"으로 단정하지 않고 알린다
			log.error("Toss 결제 조회에 실패했습니다. orderId={}", orderId, exception);

			throw new PaymentLookupFailedException("Toss 결제 조회에 실패했습니다. orderId=" + orderId, exception);
		}
		if (response == null) {
			throw new PaymentLookupFailedException("Toss 결제 조회 응답이 비어 있습니다. orderId=" + orderId, null);
		}
		return switch (TossPaymentStatus.from(response.status()).getLookupStatus()) {
			case COMPLETED -> PaymentLookupResult.completed(
				new PaymentApprovalResult(response.totalAmount(), response.paymentKey(), response.approvedAtInKorea()));
			case NOT_COMPLETED -> PaymentLookupResult.notCompleted();
			case PENDING -> PaymentLookupResult.pending();
		};
	}

	private String userMessage(ErrorResponse error) {
		if (error == null || error.message() == null || error.message().isBlank()
			|| TossErrorCode.from(error.code()).isMessageHidden()) {
			return GENERIC_REJECTION_MESSAGE;
		}
		return error.message();
	}

	private record ConfirmRequest(String paymentKey, String orderId, long amount) {
	}

	// Toss 응답(Payment 객체)엔 필드가 훨씬 많지만, 지갑에 반영할 때 쓰는 값만 받는다. status는 조회 응답에서만 쓴다
	// approvedAt은 PG 결제취소 기한의 기준이라 받는다 (Toss는 "2026-10-15T10:00:00+09:00" 형식으로 준다)
	@JsonIgnoreProperties(ignoreUnknown = true)
	private record PaymentResponse(String paymentKey, long totalAmount, String status, OffsetDateTime approvedAt) {
		LocalDateTime approvedAtInKorea() {
			return approvedAt == null ? null : approvedAt.atZoneSameInstant(KOREA).toLocalDateTime();
		}
	}
}
