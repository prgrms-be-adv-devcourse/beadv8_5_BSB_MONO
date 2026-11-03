package com.bukang.cash.out.pg;

/**
 * PG(결제대행사)에 결제 승인을 요청하고, 승인 결과를 조회한다
 * 프론트가 PG 결제창에서 받은 paymentKey·orderId·amount를 그대로 넘기면, 이 승인이 끝나야 실제로 돈이 움직인다.
 * approve가 실패하면 PaymentApprovalFailedException을 던진다. resultUnknown이면 승인됐는지 모르니 lookupByOrderId로 확인한다.
 * lookupByOrderId는 조회 자체가 실패하면 PaymentLookupFailedException을 던진다.
 */
public interface PaymentClient {
	PaymentApprovalResult approve(String paymentKey, String orderId, long amount);

	PaymentLookupResult lookupByOrderId(String orderId);
}
