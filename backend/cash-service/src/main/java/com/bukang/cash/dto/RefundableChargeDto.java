package com.bukang.cash.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 환급 화면의 충전 건 한 줄. chargeId는 예치금 내역 충전 줄의 refId와 같은 값이라 프론트가 짝을 맞출 때 쓴다
// chargedAt은 PG 승인 시각(없으면 충전 시각)이고, remainingAmount는 남은 예치금을 최근 충전부터 채운 몫이다
// lastCancelableDate는 PG 결제취소를 할 수 있는 마지막 날(이날 24:00까지, 한국 시간)이다
public record RefundableChargeDto(
	long chargeId,
	LocalDateTime chargedAt,
	long amount,
	long remainingAmount,
	LocalDate lastCancelableDate,
	boolean expired
) {
}
