package com.bukang.cash.out.pg;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.bukang.cash.domain.exception.PaymentCancelFailedException;
import com.bukang.common.global.web.IdempotencyKeyHeader;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Toss페이먼츠 결제 취소 API를 부른다 (POST /v1/payments/{paymentKey}/cancel)
 * 시크릿 키를 Basic 인증으로 보낸다. TossPaymentClient가 감싸 쓴다
 */
@Slf4j
@RequiredArgsConstructor
class TossPaymentCancelClient {
	static final String CANCEL_PATH = "/v1/payments/{paymentKey}/cancel";
	private static final String IDEMPOTENT_REQUEST_PROCESSING = "IDEMPOTENT_REQUEST_PROCESSING";

	private final RestClient restClient;
	private final String secretKey;

	PaymentCancelResult cancel(String paymentKey, String cancelReason, long cancelAmount, String idempotencyKey) {
		CancelResponse response;

		try {
			response = restClient.post()
				.uri(CANCEL_PATH, paymentKey)
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.headers(headers -> {
					headers.setBasicAuth(secretKey, "");
					headers.set(IdempotencyKeyHeader.NAME, idempotencyKey);
				})
				.body(new CancelRequest(cancelReason, cancelAmount))
				.retrieve()
				.body(CancelResponse.class);
		} catch (RestClientResponseException exception) {
			String pgCode = TossErrorCodeReader.readCode(exception);
			log.error("Toss 결제 취소가 거절됐습니다. paymentKey={}, status={}, code={}, body={}",
				paymentKey, exception.getStatusCode(), pgCode, exception.getResponseBodyAsString());
			// 5xx이거나 같은 중복 방지 키의 요청이 아직 처리 중이면 취소됐는지 알 수 없다
			boolean resultUnknown = exception.getStatusCode().is5xxServerError()
				|| IDEMPOTENT_REQUEST_PROCESSING.equals(pgCode);
			throw new PaymentCancelFailedException("PG사가 결제 취소를 거절했습니다.", pgCode, resultUnknown);
		} catch (RestClientException exception) {
			log.error("Toss 결제 취소 서버에 연결하지 못했습니다. paymentKey={}", paymentKey, exception);
			// 응답 전에 끊겼으면 Toss가 이미 취소했을 수도 있다
			throw new PaymentCancelFailedException("PG 서버에 연결하지 못했습니다. 잠시 후 다시 시도해주세요.", null, true);
		}

		// 방금 요청한 취소 건은 cancels 배열의 마지막 항목이다. 이미 취소된 결제를 다시 취소하면 Toss가 4xx로
		// 거절해 위 catch에서 끝나므로, 여기까지 내려왔다면 cancels엔 항상 이번 요청으로 새로 추가된 항목이 있다
		CancelDetail lastCancel = response == null || response.cancels() == null || response.cancels().isEmpty()
			? null
			: response.cancels().getLast();

		if (lastCancel == null) {
			// 응답은 200인데 cancels가 비어 있다 — Toss 쪽 계약 위반이다. 200이면 Toss는 취소를 받아 줬을 수 있어
			// 실패로 보고 되돌리면 돈이 두 번 나갈 수 있으므로 결과 모름으로 둔다
			log.error("Toss 결제 취소 응답이 비어 있습니다. paymentKey={}", paymentKey);
			throw new PaymentCancelFailedException("PG 서버 응답이 올바르지 않습니다.", null, true);
		}

		return new PaymentCancelResult(lastCancel.cancelAmount(), lastCancel.transactionKey());
	}

	// cancelAmount가 남은 결제 금액보다 작으면 Toss가 부분 취소한다
	private record CancelRequest(String cancelReason, long cancelAmount) {
	}

	// 취소 응답의 cancels 배열만 쓴다. 요청 금액이 아니라 이 배열의 값을 믿는다(위변조 방지)
	@JsonIgnoreProperties(ignoreUnknown = true)
	private record CancelResponse(List<CancelDetail> cancels) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record CancelDetail(long cancelAmount, String transactionKey) {
	}
}
