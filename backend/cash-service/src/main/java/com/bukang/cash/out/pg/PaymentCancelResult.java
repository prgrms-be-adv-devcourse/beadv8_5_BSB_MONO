package com.bukang.cash.out.pg;

// PG 취소 응답에서 우리가 실제로 쓰는 값만 추린다. canceledAmount는 요청한 금액이 아니라 응답의 cancels 배열 중
// 이번 취소 건의 금액이다 (위변조 방지, PaymentApprovalResult와 같은 원칙)
public record PaymentCancelResult(long canceledAmount, String transactionKey) {
}
