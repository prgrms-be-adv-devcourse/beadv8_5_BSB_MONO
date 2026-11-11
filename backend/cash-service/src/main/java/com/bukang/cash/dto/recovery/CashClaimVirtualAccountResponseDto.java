package com.bukang.cash.dto.recovery;

// 정산에 돌려주는 청구용 가상계좌. 정산은 이 계좌를 판매자에게 알린다
public record CashClaimVirtualAccountResponseDto(String bankName, String accountNumber) {
}
