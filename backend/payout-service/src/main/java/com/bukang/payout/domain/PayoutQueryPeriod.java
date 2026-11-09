package com.bukang.payout.domain;

import java.time.LocalDate;
import java.time.YearMonth;

import com.bukang.payout.domain.exception.PayoutQueryException;

/**
 * 판매자 정산 조회의 기간 한도 (payout.md 1장 조회 · 보관 2번)
 * 정산 내역은 정산 월로 최대 12개월, 건별 정산은 정산 생성일 · 지급보류는 보류한 날로 최대 1개월까지 한 번에 조회한다
 * 기준 날짜는 나중에 바뀌지 않는 값만 쓴다. 지급 예정일은 보류를 풀면 바뀌어 같은 조건의 조회 결과가 달라지므로 쓰지 않는다
 */
public final class PayoutQueryPeriod {
	private static final int MAX_PAYOUT_MONTHS = 12;
	private static final int MAX_DETAIL_MONTHS = 1;

	private PayoutQueryPeriod() {
	}

	// 시작 달을 포함해 12개 달까지 (2025-11 ~ 2026-10은 되고, 2025-11 ~ 2026-11은 안 된다)
	public static void checkPayoutMonths(YearMonth from, YearMonth to) {
		if (from.isAfter(to)) {
			throw new PayoutQueryException("조회 시작이 끝보다 늦습니다.");
		}
		if (!to.isBefore(from.plusMonths(MAX_PAYOUT_MONTHS))) {
			throw new PayoutQueryException("정산 내역은 최대 12개월까지 조회할 수 있습니다.");
		}
	}

	// 끝 날이 시작 날 + 1개월 전이어야 한다 (9/1 ~ 9/30, 9/15 ~ 10/14는 되고, 9/15 ~ 10/15는 안 된다)
	public static void checkDetailDays(LocalDate from, LocalDate to) {
		checkOneMonth(from, to, "건별 정산·지급보류 내역은 최대 1개월까지 조회할 수 있습니다.");
	}

	// 관리자 이상 감지 기록은 실행일로 최대 1개월 (BUK-108). 한도 규칙은 건별 정산과 같다
	public static void checkFailureDays(LocalDate from, LocalDate to) {
		checkOneMonth(from, to, "이상 감지 기록은 최대 1개월까지 조회할 수 있습니다.");
	}

	private static void checkOneMonth(LocalDate from, LocalDate to, String tooLongMessage) {
		if (from.isAfter(to)) {
			throw new PayoutQueryException("조회 시작이 끝보다 늦습니다.");
		}
		if (!to.isBefore(from.plusMonths(MAX_DETAIL_MONTHS))) {
			throw new PayoutQueryException(tooLongMessage);
		}
	}
}
