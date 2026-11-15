package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PayoutTest {
	private static final BigDecimal FEE_RATE = new BigDecimal("0.0550");
	private static final LocalDate TARGET_DATE = LocalDate.of(2026, 9, 26);
	private static final BusinessDayCalendar CALENDAR = new BusinessDayCalendar();

	private Payout newPayout() {
		return Payout.finalStage(1, 10, "테스트 대회", LocalDate.of(2026, 9, 25),
			LocalDateTime.of(2026, 9, 26, 3, 0), CALENDAR);
	}

	private Payout finalPayout(LocalDate raceDate) {
		return Payout.finalStage(1, 10, "테스트 대회", raceDate, raceDate.plusDays(1).atTime(3, 0), CALENDAR);
	}

	private Payout firstPayout(LocalDate refundDeadline) {
		return Payout.first(1, 10, "테스트 대회", refundDeadline, refundDeadline.plusDays(1).atTime(3, 0), CALENDAR);
	}

	// 정산 월(환불 마감일의 달)의 다음 달 20일에 지급한다. 20일이 주말이어도 그날 지급한다 (11/2 결정, 날짜 규칙 통일)
	// 2026년 20일: 6/20 토, 9/20 일, 10/20 화, 11/20 금, 12/20 일, 2027-01-20 수
	@ParameterizedTest(name = "환불 마감일 {0} → {1} 지급")
	@CsvSource({
		"2026-10-11, 2026-11-20", // 10월 마감 → 11/20 금
		"2026-10-01, 2026-11-20", // 달의 첫날이어도 다음 달 20일
		"2026-10-31, 2026-11-20", // 달의 끝날이어도 정산 월은 마감일의 달(2026-10)
		"2026-10-25, 2026-11-20", // 마감일이 20일 뒤여도 다음 달 20일
		"2026-09-15, 2026-10-20", // 9월 마감 → 10/20 화
		"2026-08-15, 2026-09-20", // TC-DAY-1-1 9/20 일요일에도 그날 지급
		"2026-05-15, 2026-06-20", // TC-DAY-1-2 6/20 토요일에도 그날 지급
		"2026-12-31, 2027-01-20" // 해가 바뀌어도 같다
	})
	@DisplayName("1차 정산의 지급 예정일은 정산 월(환불 마감일의 달)의 다음 달 20일이고, 주말이어도 그날이다")
	void firstPayDateIsTwentiethOfNextMonth(LocalDate refundDeadline, LocalDate expectedPayDate) {
		Payout payout = firstPayout(refundDeadline);

		assertThat(payout.getScheduledPayDate()).isEqualTo(expectedPayDate);
		assertThat(payout.getPayoutMonth()).isEqualTo(YearMonth.from(refundDeadline).toString());
	}

	// 최종도 정산 월(대회일의 달)의 다음 달 20일이다. 마감일과 대회일이 같은 달이면 1차와 같은 날 지급된다 (10/29 결정)
	@ParameterizedTest(name = "대회일 {0} → {1} 지급")
	@CsvSource({
		"2026-10-17, 2026-11-20", // 10월 대회 → 11/20 금
		"2026-10-31, 2026-11-20", // 달의 끝날이어도 정산 월은 대회일의 달(2026-10)
		"2026-11-01, 2026-12-20", // TC-DAY-1-3 11월 대회 → 12/20 일요일에도 그날 지급
		"2026-08-25, 2026-09-20", // TC-DAY-1-4 9/20 일요일에도 그날 지급
		"2026-12-20, 2027-01-20" // 해가 바뀌어도 같다
	})
	@DisplayName("최종 정산의 지급 예정일은 정산 월(대회일의 달)의 다음 달 20일이고, 주말이어도 그날이다")
	void finalPayDateIsTwentiethOfNextMonth(LocalDate raceDate, LocalDate expectedPayDate) {
		Payout payout = finalPayout(raceDate);

		assertThat(payout.getScheduledPayDate()).isEqualTo(expectedPayDate);
		assertThat(payout.getPayoutMonth()).isEqualTo(YearMonth.from(raceDate).toString());
	}

	@Test
	@DisplayName("5만 원 참가권 2장이면 판매액 100,000, 수수료 5,500, 지급액 94,500이다")
	void amountsForTwoEntries() {
		Payout payout = newPayout();

		payout.addItem(100, 50_000L, FEE_RATE, TARGET_DATE);
		payout.addItem(101, 50_000L, FEE_RATE, TARGET_DATE);

		assertThat(payout.getSalesAmount()).isEqualTo(100_000L);
		assertThat(payout.getRefundAmount()).isZero();
		assertThat(payout.getFeeAmount()).isEqualTo(5_500L);
		assertThat(payout.getPayoutAmount()).isEqualTo(94_500L);
	}

	@Test
	@DisplayName("수수료는 장마다 원 미만을 버리고 합한다 (10,010원 × 10장: 합계로 계산한 5,505가 아니라 5,500)")
	void feeIsFlooredPerEntry() {
		Payout payout = newPayout();

		for (long orderItemId = 1; orderItemId <= 10; orderItemId++) {
			payout.addItem(orderItemId, 10_010L, FEE_RATE, TARGET_DATE);
		}

		assertThat(payout.getFeeAmount()).isEqualTo(5_500L);
		assertThat(payout.getPayoutAmount()).isEqualTo(100_100L - 5_500L);
	}

	@Test
	@DisplayName("1차는 판매 집계로 1장 몫(가격 × 40% 버림) × 장수를 1차 정산금 한 줄로 쓴다 (33,333원 × 10장 → 133,330, 합계에 곱한 133,332가 아니다)")
	void firstPayoutIsFortyPercentPerEntry() {
		Payout first = Payout.first(1, 10, "테스트 대회", LocalDate.of(2026, 9, 30),
			LocalDateTime.of(2026, 10, 1, 3, 0), CALENDAR);

		first.recordAdvance(10, 333_330L, 10, LocalDate.of(2026, 10, 1));

		assertThat(first.getPayoutStage()).isEqualTo(PayoutStage.일차_정산);
		assertThat(first.getSalesAmount()).isEqualTo(333_330L);
		assertThat(first.getFeeAmount()).isZero();
		assertThat(first.getPayoutAmount()).isEqualTo(133_330L);
		assertThat(first.getItems()).singleElement()
			.satisfies(item -> assertThat(item.getRecordType()).isEqualTo(PayoutRecordType.선금));
	}

	@Test
	@DisplayName("가격 합이 장수로 나누어떨어지지 않으면(가격이 섞임) 1차 정산금을 만들지 않는다")
	void firstPayoutRejectsMixedPrices() {
		Payout first = Payout.first(1, 10, "테스트 대회", LocalDate.of(2026, 9, 30),
			LocalDateTime.of(2026, 10, 1, 3, 0), CALENDAR);

		assertThatThrownBy(() -> first.recordAdvance(10, 83_333L, 2, LocalDate.of(2026, 10, 1)))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("최종은 판매자 정산액에서 1차 정산금 차감을 뺀다 (33,333원 × 10장, 1차 133,330 → 181,670)")
	void finalPayoutDeductsAdvance() {
		Payout last = Payout.finalStage(1, 10, "테스트 대회", LocalDate.of(2026, 10, 17),
			LocalDateTime.of(2026, 10, 18, 3, 0), CALENDAR);
		last.deductAdvance(133_330L);

		for (long orderItemId = 1; orderItemId <= 10; orderItemId++) {
			last.addItem(orderItemId, 33_333L, FEE_RATE, TARGET_DATE);
		}

		assertThat(last.getPayoutStage()).isEqualTo(PayoutStage.최종_정산);
		assertThat(last.getFeeAmount()).isEqualTo(18_330L);
		assertThat(last.getAdvanceDeductionAmount()).isEqualTo(133_330L);
		assertThat(last.getPayoutAmount()).isEqualTo(181_670L);
	}
}
