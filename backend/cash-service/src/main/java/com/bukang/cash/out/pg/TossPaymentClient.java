package com.bukang.cash.out.pg;

import org.springframework.web.client.RestClient;

/**
 * Toss페이먼츠 결제 승인·조회 API를 부른다. 실제 HTTP 호출은 TossPaymentConfirmClient가 맡고, 이 클래스는
 * PaymentClient 계약을 구현하는 얇은 창구다. 등록은 PaymentClientConfig가 한다 (cash.clients.pg.mode=http)
 */
public class TossPaymentClient implements PaymentClient {
	private final TossPaymentConfirmClient confirmClient;

	// 승인은 돈이 움직여 응답 시간 제한이 넉넉한 confirmRestClient를, 조회는 짧은 lookupRestClient를 쓴다
	public TossPaymentClient(RestClient confirmRestClient, RestClient lookupRestClient, String secretKey) {
		this.confirmClient = new TossPaymentConfirmClient(confirmRestClient, lookupRestClient, secretKey);
	}

	@Override
	public PaymentApprovalResult approve(String paymentKey, String orderId, long amount) {
		return confirmClient.approve(paymentKey, orderId, amount);
	}

	@Override
	public PaymentLookupResult lookupByOrderId(String orderId) {
		return confirmClient.lookupByOrderId(orderId);
	}
}
