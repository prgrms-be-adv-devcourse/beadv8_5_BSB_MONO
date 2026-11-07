package com.bukang.cash.domain.charge;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class CashChargePolicy {
	public static final long MINIMUM_AMOUNT = 10_000L;

	private CashChargePolicy() {
	}

	// 정책 5장 2차 1 「PG 결제취소와 기한」: 승인일 + 1년 − 1일의 24:00(한국 시간)까지 PG 결제취소할 수 있다
	public static LocalDate lastCancelableDate(LocalDateTime approvedAt) {
		return approvedAt.toLocalDate().plusYears(1).minusDays(1);
	}

	// now는 한국 시간이어야 한다. 마지막 날 24:00 = 다음 날 00:00이 되는 순간 기한이 지난다
	public static boolean isCancelable(LocalDateTime approvedAt, LocalDateTime now) {
		return now.isBefore(lastCancelableDate(approvedAt).plusDays(1).atStartOfDay());
	}
}
