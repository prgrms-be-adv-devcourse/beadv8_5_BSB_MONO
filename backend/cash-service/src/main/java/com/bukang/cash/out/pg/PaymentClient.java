package com.bukang.cash.out.pg;

import java.util.Optional;

/**
 * PG(결제대행사)에 결제 승인을 요청한다 (PRO-81)
 * 프론트가 PG 결제창에서 받은 paymentKey·orderId·amount를 그대로 넘기면, 이 승인이 끝나야 실제로 돈이 움직인다.
 * 실패하면 PaymentApprovalFailedException을 던진다.
 */
public interface PaymentClient {
	PaymentApprovalResult approve(String paymentKey, String orderId, long amount);

	// orderId로 PG에 실제 결제 상태를 재조회한다 (PRO-200). 승인이 끝난 상태면 그 결과를, 아니면(진행 중·취소·
	// 조회 실패 등) 빈 값을 돌려준다. 타임아웃 뒤 재시도가 "이미 처리된 결제" 거절을 받았을 때만 쓴다.
	Optional<PaymentApprovalResult> findCompletedByOrderId(String orderId);
}
