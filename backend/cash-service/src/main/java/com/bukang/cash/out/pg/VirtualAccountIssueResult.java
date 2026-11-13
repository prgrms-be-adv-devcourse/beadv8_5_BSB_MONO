package com.bukang.cash.out.pg;

import java.time.LocalDateTime;

// PG 가상계좌 발급 응답에서 우리가 쓰는 값만 추린다
// amount는 PG가 이 계좌로 받기로 한 금액이다. 주문번호로 찾은 계좌가 이 청구 것인지 맞춰 볼 때 쓴다
// dueDate는 입금 기한(한국 시간)이고, secret은 입금 알림 웹훅이 진짜 PG가 보낸 것인지 맞춰 볼 값이다
public record VirtualAccountIssueResult(
	String bankName, String accountNumber, long amount, LocalDateTime dueDate, String secret
) {
}
