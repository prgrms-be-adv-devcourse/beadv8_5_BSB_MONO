package com.bukang.cash.dto.payment;

import java.time.LocalDate;
import java.util.List;

// 환급 화면용 충전 내역. charges는 최근 충전부터
// earliestLastCancelableDate는 남은 금액이 있고 기한이 지나지 않은 충전 중 가장 빠른 마지막 날이다. 없으면 null
public record RefundableChargesResponseDto(
	long balance,
	LocalDate earliestLastCancelableDate,
	List<RefundableChargeDto> charges
) {
}
