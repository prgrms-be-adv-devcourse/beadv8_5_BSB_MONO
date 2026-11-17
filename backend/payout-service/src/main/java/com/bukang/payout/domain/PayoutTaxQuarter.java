package com.bukang.payout.domain;

import java.time.LocalDate;
import java.time.YearMonth;

import com.bukang.payout.domain.exception.PayoutQueryException;

/**
 * 통신판매중개자 판매 자료를 내는 분기 (BUK-246). 부가가치세법 75조: 분기 말일의 다음 달 15일까지 낸다
 */
public record PayoutTaxQuarter(int year, int quarter) {
	private static final int DUE_DAY = 15;

	public PayoutTaxQuarter {
		if (quarter < 1 || quarter > 4) {
			throw new PayoutQueryException("분기는 1 이상 4 이하여야 합니다.");
		}
	}

	public LocalDate from() {
		return YearMonth.of(year, quarter * 3 - 2).atDay(1);
	}

	public LocalDate to() {
		return YearMonth.of(year, quarter * 3).atEndOfMonth();
	}

	public LocalDate dueDate() {
		return YearMonth.of(year, quarter * 3).plusMonths(1).atDay(DUE_DAY);
	}
}
