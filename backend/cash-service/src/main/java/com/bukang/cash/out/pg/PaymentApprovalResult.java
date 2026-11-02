package com.bukang.cash.out.pg;

// PG 승인 응답에서 우리가 실제로 쓰는 값만 추린다. 지갑에 반영할 땐 이 approvedAmount를 기준으로 하고,
// 요청 바디로 받은 amount를 그대로 믿지 않는다 (위변조 방지)
public record PaymentApprovalResult(long approvedAmount, String pgPaymentKey) {
}
