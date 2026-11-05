package com.bukang.cash.dto;

import java.time.LocalDate;

// 환급 화면의 충전 건 한 줄. remainingAmount는 남은 예치금을 최근 충전부터 채운 몫이다
// lastCancelableDate는 PG 결제취소를 할 수 있는 마지막 날(이날 24:00까지, 한국 시간)이다
public record RefundableChargeDto(long amount, long remainingAmount, LocalDate lastCancelableDate, boolean expired) {
}
