package com.bukang.payout.out;

/**
 * 청구 하나에 발급한 가상계좌. 판매자는 이 계좌로 청구 금액을 입금한다 (BUK-88)
 */
public record ClaimVirtualAccount(String bankName, String accountNumber) {
}
