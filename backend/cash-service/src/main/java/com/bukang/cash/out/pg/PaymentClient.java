package com.bukang.cash.out.pg;

/**
 * PG(결제대행사)에 결제 승인을 요청하고, 승인 결과를 조회하고, 결제를 취소한다
 * 프론트가 PG 결제창에서 받은 paymentKey·orderId·amount를 그대로 넘기면, 이 승인이 끝나야 실제로 돈이 움직인다.
 * approve가 실패하면 PaymentApprovalFailedException을 던진다. resultUnknown이면 승인됐는지 모르니 lookupByOrderId로 확인한다.
 * lookupByOrderId는 조회 자체가 실패하면 PaymentLookupFailedException을 던진다.
 */
public interface PaymentClient {
	PaymentApprovalResult approve(String paymentKey, String orderId, long amount);

	PaymentLookupResult lookupByOrderId(String orderId);

	// paymentKey로 결제 중 cancelAmount만큼 취소한다. 남은 결제 금액과 같으면 전액 취소가 된다.
	// idempotencyKey가 같은 요청은 PG가 한 번만 처리하므로, 결과를 모를 때 같은 키로 다시 불러 확인한다.
	// 실패하면 PaymentCancelFailedException을 던진다.
	PaymentCancelResult cancel(String paymentKey, String cancelReason, long cancelAmount, String idempotencyKey);

	// orderId로 amount를 받을 가상계좌를 발급한다. 판매자가 갚을 돈(청구)마다 하나씩 쓴다
	VirtualAccountIssueResult issueVirtualAccount(String orderId, long amount);
}
