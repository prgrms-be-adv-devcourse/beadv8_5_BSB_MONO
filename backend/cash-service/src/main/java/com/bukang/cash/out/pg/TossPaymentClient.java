package com.bukang.cash.out.pg;

import org.springframework.web.client.RestClient;

/**
 * Toss페이먼츠 결제 승인·조회·취소 API를 부른다. 실제 HTTP 호출은 책임별로 나눈
 * TossPaymentConfirmClient(승인·조회)와 TossPaymentCancelClient(취소)가 맡고, 이 클래스는 그 둘을 묶어
 * PaymentClient 계약을 구현하는 얇은 창구다. 등록은 PaymentClientConfig가 한다 (cash.clients.pg.mode=http)
 */
public class TossPaymentClient implements PaymentClient {
	private final TossPaymentConfirmClient confirmClient;
	private final TossPaymentCancelClient cancelClient;

	// 승인과 취소는 돈이 움직여 응답 시간 제한이 넉넉한 moneyMovingRestClient를, 조회는 짧은 lookupRestClient를 쓴다
	public TossPaymentClient(RestClient moneyMovingRestClient, RestClient lookupRestClient, String secretKey) {
		this.confirmClient = new TossPaymentConfirmClient(moneyMovingRestClient, lookupRestClient, secretKey);
		this.cancelClient = new TossPaymentCancelClient(moneyMovingRestClient, secretKey);
	}

	@Override
	public PaymentApprovalResult approve(String paymentKey, String orderId, long amount) {
		return confirmClient.approve(paymentKey, orderId, amount);
	}

	@Override
	public PaymentLookupResult lookupByOrderId(String orderId) {
		return confirmClient.lookupByOrderId(orderId);
	}

	@Override
	public PaymentCancelResult cancel(String paymentKey, String cancelReason, long cancelAmount) {
		return cancelClient.cancel(paymentKey, cancelReason, cancelAmount);
	}
}
