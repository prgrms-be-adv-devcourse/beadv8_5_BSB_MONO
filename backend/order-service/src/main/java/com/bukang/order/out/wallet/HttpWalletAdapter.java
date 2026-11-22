package com.bukang.order.out.wallet;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bukang.common.global.rsdata.RsData;
import com.bukang.order.domain.exception.ExternalServiceRejectedException;
import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.domain.exception.OrderNotPayableException;
import com.bukang.order.domain.exception.PaymentBusyException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 예치금 서비스(D)의 내부 API로 결제·결제 취소·환불을 요청한다 (PRO-158, D PRO-79·80·83)
 * POST /internal/v1/cash/payments/{orderId} {memberId, amount} → {outcome, shortage}
 * POST /internal/v1/cash/payments/{orderId}/cancel {memberId}
 * POST /internal/v1/cash/payments/{orderId}/refund {memberId} → {amount}
 * 응답이 없거나 형식이 다르면 D가 처리했는지 알 수 없으므로("모름") 주문을 바꾸지 않고 503으로 다시 시도하게 한다
 * 409(이미 취소·환불된 결제, 결제한 회원과 다름)는 다시 해도 풀리지 않으므로 결제는 409로, 결제 취소·환불은 503으로 알린다
 * 같은 주문 ID로 다시 결제하면 D가 한 번만 차감하고, 포기한 주문은 만료 처리가 결제 취소로 돌려받는다 (WalletPort)
 * 등록은 CashClientConfig가 한다 (order.clients.cash.mode=http)
 * 결제만 동시 호출 수를 제한한다(BUK-235). 막 뜬 예치금이 느릴 때 결제가 한꺼번에 몰려 더 느려지는 것을 막는다
 * 결제 취소·환불은 제한하지 않는다: 같은 문을 쓰면 결제가 몰릴 때 돈을 돌려주는 호출이 밀린다
 */
@Slf4j
public class HttpWalletAdapter implements WalletPort {
	static final String PAY_PATH = "/internal/v1/cash/payments/{orderId}";
	static final String CANCEL_PATH = "/internal/v1/cash/payments/{orderId}/cancel";
	static final String REFUND_PATH = "/internal/v1/cash/payments/{orderId}/refund";
	static final String PAY_UNKNOWN_MESSAGE = "결제 결과를 확인하지 못했습니다. 잠시 뒤 다시 결제해주세요.";
	static final String UNAVAILABLE_MESSAGE = "예치금 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.";
	static final String PAYMENT_FINALIZED_MESSAGE = "이미 취소·환불된 결제라 다시 결제할 수 없습니다.";

	private static final ParameterizedTypeReference<RsData<PayBody>> PAY_TYPE = new ParameterizedTypeReference<>() {
	};

	private final RestClient restClient;
	// 결제 동시 호출 자리. 주문 서버 한 대 기준이다(서버가 2대면 예치금에는 합쳐 두 배가 간다)
	private final Semaphore payPermits;
	private final long payAcquireTimeoutMillis;

	// 제한 없음 (테스트용)
	public HttpWalletAdapter(RestClient restClient) {
		this(restClient, Integer.MAX_VALUE, Duration.ZERO);
	}

	public HttpWalletAdapter(RestClient restClient, int maxConcurrentPay, Duration payAcquireTimeout) {
		if (maxConcurrentPay < 1 || payAcquireTimeout.isNegative()) {
			throw new IllegalArgumentException("예치금 결제 동시 호출 수는 1 이상, 자리 기다림은 0 이상이어야 합니다.");
		}
		this.restClient = restClient;
		this.payPermits = new Semaphore(maxConcurrentPay);
		this.payAcquireTimeoutMillis = payAcquireTimeout.toMillis();
	}

	/**
	 * 동시 호출 자리를 기다렸다가(order.clients.cash.pay-acquire-timeout) 결제를 보낸다
	 * 자리를 못 받으면 예치금을 부르지 않고 PaymentBusyException(503)을 던진다. 돈이 빠지지 않은 것이 확실한 실패다
	 */
	@Override
	public WalletPayResult pay(long orderId, long memberId, long amount) {
		if (!acquirePayPermit()) {
			log.warn("예치금 결제 동시 호출 자리를 받지 못해 결제를 보내지 않았습니다. orderId={}", orderId);
			throw new PaymentBusyException();
		}
		try {
			return send(orderId, memberId, amount);
		} finally {
			payPermits.release();
		}
	}

	private boolean acquirePayPermit() {
		try {
			return payPermits.tryAcquire(payAcquireTimeoutMillis, TimeUnit.MILLISECONDS);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private WalletPayResult send(long orderId, long memberId, long amount) {
		RsData<PayBody> response;
		try {
			response = restClient.post()
				.uri(PAY_PATH, orderId)
				.contentType(MediaType.APPLICATION_JSON)
				.body(new PayRequest(memberId, amount))
				.retrieve()
				.body(PAY_TYPE);
		} catch (HttpClientErrorException exception) {
			// D가 409로 거절한 결제는 다시 시도해도 풀리지 않는다. D가 차감하지 않은 것이 확실하므로 D의 이유를 그대로 알린다
			// (결제 취소로 막아 둔 주문: 결제 기한이 지나 만료 처리가 돌려받은 주문 등, 결제한 회원과 요청한 회원이 다름)
			if (exception.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
				throw new OrderNotPayableException(messageOf(exception, PAYMENT_FINALIZED_MESSAGE));
			}
			throw contractError("결제", orderId, exception);
		} catch (RestClientException exception) {
			// 응답을 못 받았으니 D가 차감했는지 모른다. 주문은 결제 대기로 남고, 같은 주문 ID로 다시 결제하면 된다
			log.warn("예치금 서비스에 결제를 요청했지만 결과를 받지 못했습니다. orderId={}, cause={}", orderId, exception.toString());
			throw new ExternalServiceUnavailableException(PAY_UNKNOWN_MESSAGE);
		}
		PayBody body = response == null ? null : response.data();
		// 결과가 없거나, 잔액 부족인데 부족 금액이 없으면 계약이 어긋난 것이다. 결제됐는지 모르는 것으로 본다
		if (body == null || body.outcome() == null
			|| (body.outcome() == WalletPayResult.Outcome.INSUFFICIENT_BALANCE && body.shortage() == null)) {
			log.error("예치금 서비스의 결제 응답 형식이 계약과 다릅니다. orderId={}", orderId);
			throw new ExternalServiceUnavailableException(PAY_UNKNOWN_MESSAGE);
		}
		return switch (body.outcome()) {
			case PAID -> WalletPayResult.paid();
			case INSUFFICIENT_BALANCE -> WalletPayResult.insufficient(body.shortage());
		};
	}

	// 실패하면 예외를 던진다. 결제 쪽은 롤백되어 다시 시도하고, 만료 처리는 그 주문을 다음 실행에서 다시 부른다
	@Override
	public void cancelPayment(long orderId, long memberId) {
		postForSuccess(CANCEL_PATH, "결제 취소", orderId, memberId);
	}

	// D 환불 API(PRO-83, PR #77)는 10/14 김소희님이 정한 경로·요청이다. 이미 환불·결제 취소된 주문이면 D가 그대로 성공한다
	// 응답의 환불 금액은 아직 쓰지 않는다
	@Override
	public void refund(long orderId, long memberId) {
		postForSuccess(REFUND_PATH, "환불", orderId, memberId);
	}

	private void postForSuccess(String path, String action, long orderId, long memberId) {
		try {
			restClient.post()
				.uri(path, orderId)
				.contentType(MediaType.APPLICATION_JSON)
				.body(new MemberRequest(memberId))
				.retrieve()
				.toBodilessEntity();
		} catch (HttpClientErrorException exception) {
			throw contractError(action, orderId, exception);
		} catch (RestClientException exception) {
			// 만료 처리는 실패한 주문을 매분 다시 부르므로, D 장애 때 로그가 쌓이지 않게 스택 없이 한 줄로 남긴다
			log.warn("예치금 서비스에 {}를 요청하지 못했습니다. orderId={}, cause={}", action, orderId, exception.toString());
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
	}

	// 4xx는 다시 시도해도 풀리지 않는다. 경로·요청 계약이 어긋났거나 지갑이 아직 없을 수 있어 장애와 구분해 남긴다
	// D가 처리하지 않은 것은 확실하므로 결제도 "결과를 모름"이 아니라 "연결하지 못함"으로 알린다
	// 다시 해도 같으므로 장애와 다른 예외로 던진다 (대회 취소 환불이 연속 장애로 세지 않게)
	private ExternalServiceRejectedException contractError(String action, long orderId,
		HttpClientErrorException exception) {
		log.error("예치금 서비스가 {} 요청을 거절했습니다. 계약(경로·요청)이나 지갑을 확인해야 합니다. status={}, orderId={}",
			action, exception.getStatusCode(), orderId);
		return new ExternalServiceRejectedException(UNAVAILABLE_MESSAGE);
	}

	// D 오류 응답(RsData)의 message. 읽지 못하면 기본 문구를 쓴다
	private static String messageOf(HttpClientErrorException exception, String fallback) {
		try {
			ErrorBody body = exception.getResponseBodyAs(ErrorBody.class);
			return body == null || body.message() == null || body.message().isBlank() ? fallback : body.message();
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	record PayRequest(long memberId, long amount) {
	}

	record MemberRequest(long memberId) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ErrorBody(String message) {
	}

	// D 응답의 data (cash CashPaymentDto). 결제 판정에 쓰는 값만 받는다
	@JsonIgnoreProperties(ignoreUnknown = true)
	record PayBody(WalletPayResult.Outcome outcome, Long shortage) {
	}
}
