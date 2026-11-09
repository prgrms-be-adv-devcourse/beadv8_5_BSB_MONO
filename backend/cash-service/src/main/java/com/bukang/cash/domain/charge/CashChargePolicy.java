package com.bukang.cash.domain.charge;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class CashChargePolicy {
	public static final long MINIMUM_AMOUNT = 10_000L;
	public static final long MAX_PER_CHARGE = 300_000L;
	public static final long MAX_PER_DAY = 1_000_000L;
	public static final long MAX_BALANCE = 10_000_000L;
	// 5분 안에 이 횟수를 넘겨 시도하면(4번째부터) 10분 쿨다운이 걸린다
	public static final long MAX_CHARGE_ATTEMPTS_PER_WINDOW = 3L;
	// 1시간 안에 PG가 명확히 거절한 횟수가 이 수를 넘으면(6번째부터) 1시간 충전 잠금이 걸린다
	public static final long MAX_CHARGE_FAILURES_PER_WINDOW = 5L;

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
