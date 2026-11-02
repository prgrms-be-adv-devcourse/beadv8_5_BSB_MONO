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

	// 2026-10-11 일 ~ 10-17 토. 마감일을 1일째로 세어 3일째(마감일 + 2일)에 지급한다. 주말이어도 그날 지급한다 (10/28 결정)
	@ParameterizedTest(name = "환불 마감일 {0} → {1} 지급")
	@CsvSource({
		"2026-10-11, 2026-10-13", // 일 → 화
		"2026-10-12, 2026-10-14", // 월 → 수
		"2026-10-13, 2026-10-15", // 화 → 목
		"2026-10-14, 2026-10-16", // 수 → 금
		"2026-10-15, 2026-10-17", // 목 → 토 (주말에도 지급)
		"2026-10-16, 2026-10-18", // 금 → 일 (주말에도 지급)
		"2026-10-17, 2026-10-19", // 토 → 월
		"2026-10-30, 2026-11-01", // 달이 바뀌어도 마감일 + 2일이다. 정산 월은 마감일의 달(2026-10)
		"2026-12-31, 2027-01-02" // 해가 바뀌어도 같다
	})
	@DisplayName("1차 정산의 지급 예정일은 환불 마감일 + 2일이고, 정산 월은 환불 마감일의 달이다")
	void firstPayDateIsRefundDeadlinePlus2Days(LocalDate refundDeadline, LocalDate expectedPayDate) {
		Payout payout = firstPayout(refundDeadline);

		assertThat(payout.getScheduledPayDate()).isEqualTo(expectedPayDate);
		assertThat(payout.getPayoutMonth()).isEqualTo(YearMonth.from(refundDeadline).toString());
	}

	// 대회일을 1일째로 세어 3일째(대회일 + 2일)에 지급한다. 주말이어도 그날 지급한다 (10/28 결정)
	@ParameterizedTest(name = "대회일 {0} → {1} 지급")
	@CsvSource({
		"2026-10-18, 2026-10-20", // 일 → 화
		"2026-10-17, 2026-10-19", // 토 → 월
		"2026-10-16, 2026-10-18", // 금 → 일 (주말에도 지급)
		"2026-10-15, 2026-10-17", // 목 → 토 (주말에도 지급)
		"2026-10-12, 2026-10-14", // 월 → 수
		"2026-10-31, 2026-11-02" // 달이 바뀌어도 대회일 + 2일이다. 정산 월은 대회일의 달(2026-10)
	})
	@DisplayName("최종 정산의 지급 예정일은 대회일 + 2일이고, 정산 월은 대회일의 달이다")
	void finalPayDateIsRaceDatePlus2Days(LocalDate raceDate, LocalDate expectedPayDate) {
		Payout payout = finalPayout(raceDate);

		assertThat(payout.getScheduledPayDate()).isEqualTo(expectedPayDate);
		assertThat(payout.getPayoutMonth()).isEqualTo(YearMonth.from(raceDate).toString());
	}

	@Test
	@DisplayName("5만 원 참가권 2장이면 판매액 100,000, 수수료 5,500, 지급액 94,500이다")
	void amountsForTwoTickets() {
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
	void feeIsFlooredPerTicket() {
		Payout payout = newPayout();

		for (long orderItemId = 1; orderItemId <= 10; orderItemId++) {
			payout.addItem(orderItemId, 10_010L, FEE_RATE, TARGET_DATE);
		}

		assertThat(payout.getFeeAmount()).isEqualTo(5_500L);
		assertThat(payout.getPayoutAmount()).isEqualTo(100_100L - 5_500L);
	}

	@Test
	@DisplayName("1차는 판매 집계로 1장 몫(가격 × 40% 버림) × 장수를 1차 정산금 한 줄로 쓴다 (33,333원 × 10장 → 133,330, 합계에 곱한 133,332가 아니다)")
	void firstPayoutIsFortyPercentPerTicket() {
		Payout first = Payout.first(1, 10, "테스트 대회", LocalDate.of(2026, 9, 30),
			LocalDateTime.of(2026, 10, 1, 3, 0), CALENDAR);

		first.recordAdvance(10, 333_330L, 10, LocalDate.of(2026, 10, 1));

		assertThat(first.getPayoutStage()).isEqualTo(PayoutStage.FIRST);
		assertThat(first.getSalesAmount()).isEqualTo(333_330L);
		assertThat(first.getFeeAmount()).isZero();
		assertThat(first.getPayoutAmount()).isEqualTo(133_330L);
		assertThat(first.getItems()).singleElement()
			.satisfies(item -> assertThat(item.getRecordType()).isEqualTo(PayoutRecordType.ADVANCE_PAID));
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

		assertThat(last.getPayoutStage()).isEqualTo(PayoutStage.FINAL);
		assertThat(last.getFeeAmount()).isEqualTo(18_330L);
		assertThat(last.getAdvanceDeductionAmount()).isEqualTo(133_330L);
		assertThat(last.getPayoutAmount()).isEqualTo(181_670L);
	}
}
