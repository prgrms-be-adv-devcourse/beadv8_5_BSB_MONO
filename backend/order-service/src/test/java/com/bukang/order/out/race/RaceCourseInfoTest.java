package com.bukang.order.out.race;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.common.shared.order.event.OrderItemRefundReason;
import com.bukang.order.support.StubRaceCoursePort;

class RaceCourseInfoTest {
	private static final LocalDateTime RACE_DATE = LocalDateTime.of(2026, 11, 15, 8, 0);
	private static final LocalDateTime OPEN_AT = LocalDateTime.of(2026, 10, 1, 10, 0);
	private static final LocalDateTime CLOSE_AT = LocalDateTime.of(2026, 10, 31, 23, 59);
	private static final LocalDate REFUND_DEADLINE = LocalDate.of(2026, 11, 5);

	@Test
	@DisplayName("나이는 대회 당일의 만 나이로 따진다. 대회 날이 생일이면 그날 한 살 많다")
	void ageOnRaceDay() {
		RaceCourseInfo course = course(14, null);

		assertThat(course.ageOnRaceDay(LocalDate.of(2012, 11, 15))).isEqualTo(14);
		assertThat(course.ageOnRaceDay(LocalDate.of(2012, 11, 16))).isEqualTo(13);
	}

	@Test
	@DisplayName("최소 나이와 최대 나이 경계값을 포함한다. 최대 나이가 없으면 위로 제한이 없다")
	void allowsAge() {
		RaceCourseInfo limited = course(20, 60);
		assertThat(limited.allowsAge(LocalDate.of(2006, 11, 15))).isTrue();
		assertThat(limited.allowsAge(LocalDate.of(2006, 11, 16))).isFalse();
		assertThat(limited.allowsAge(LocalDate.of(1966, 11, 16))).isTrue();
		assertThat(limited.allowsAge(LocalDate.of(1965, 11, 15))).isFalse();

		RaceCourseInfo noMax = course(14, null);
		assertThat(noMax.allowsAge(LocalDate.of(1930, 1, 1))).isTrue();
		assertThat(noMax.ageRangeLabel()).isEqualTo("만 14세 이상");
		assertThat(limited.ageRangeLabel()).isEqualTo("만 20세 ~ 만 60세");
	}

	@Test
	@DisplayName("접수 기간은 오픈 시각부터 마감 시각 전까지다")
	void accepting() {
		RaceCourseInfo course = course(14, null);

		assertThat(course.isAccepting(OPEN_AT.minusSeconds(1))).isFalse();
		assertThat(course.isAccepting(OPEN_AT)).isTrue();
		assertThat(course.isAccepting(CLOSE_AT.minusSeconds(1))).isTrue();
		assertThat(course.isAccepting(CLOSE_AT)).isFalse();
		assertThat(course.isBeforeClose(OPEN_AT.minusDays(1))).isTrue();
		assertThat(course.isBeforeClose(CLOSE_AT)).isFalse();
	}

	@Test
	@DisplayName("환불 기한 날짜까지(그날 포함) 환불할 수 있고 다음 날부터는 안 된다")
	void refundable() {
		RaceCourseInfo course = course(14, null);

		assertThat(course.isRefundableOn(REFUND_DEADLINE.minusDays(1))).isTrue();
		assertThat(course.isRefundableOn(REFUND_DEADLINE)).isTrue();
		assertThat(course.isRefundableOn(REFUND_DEADLINE.plusDays(1))).isFalse();
	}

	@Test
	@DisplayName("일정이 바뀌면 환불 기한이 지났어도 바뀐 날(알린 날)을 1일째로 7일째 날까지 전액 환불되고, 8일째부터는 안 된다")
	void refundableAfterScheduleChange() {
		// 환불 기한(11/5)이 지난 11/8 14:00에 대회 일정이 바뀌었다 → 11/8이 1일째, 7일째인 11/14까지
		LocalDateTime changedAt = LocalDateTime.of(2026, 11, 8, 14, 0);
		RaceCourseInfo course = courseChangedAt(changedAt);

		assertThat(course.isRefundableOn(LocalDate.of(2026, 11, 8))).isTrue();
		assertThat(course.isRefundableOn(LocalDate.of(2026, 11, 14))).isTrue();
		assertThat(course.isRefundableOn(LocalDate.of(2026, 11, 15))).isFalse();
		assertThat(course.isWithinScheduleChangeRefund(LocalDate.of(2026, 11, 14))).isTrue();
		assertThat(course.isWithinScheduleChangeRefund(LocalDate.of(2026, 11, 15))).isFalse();
		assertThat(course.scheduleChangeRefundLastDay()).isEqualTo(LocalDate.of(2026, 11, 14));
	}

	@Test
	@DisplayName("일정 변경 7일이 환불 기한보다 먼저 끝나면 환불 기한까지는 그대로 환불된다")
	void scheduleChangeDoesNotShortenRefundDeadline() {
		// 10/1에 바뀌어 10/8에 7일이 끝나도, 환불 기한(11/5)까지는 원래대로 환불된다
		RaceCourseInfo course = courseChangedAt(LocalDateTime.of(2026, 10, 1, 9, 0));

		assertThat(course.isWithinScheduleChangeRefund(LocalDate.of(2026, 10, 9))).isFalse();
		assertThat(course.isRefundableOn(LocalDate.of(2026, 10, 9))).isTrue();
		assertThat(course.isRefundableOn(REFUND_DEADLINE)).isTrue();
		assertThat(course.isRefundableOn(REFUND_DEADLINE.plusDays(1))).isFalse();
	}

	@Test
	@DisplayName("일정이 바뀐 적 없으면 일정 변경 환불 기간은 없다")
	void noScheduleChange() {
		RaceCourseInfo course = course(14, null);

		assertThat(course.isWithinScheduleChangeRefund(REFUND_DEADLINE.plusDays(1))).isFalse();
	}

	@Test
	@DisplayName("취소된 대회는 접수 기간 안이어도 신청·담기를 막고, 환불 기한이 지나도 전액 환불한다 (정책 5장 1-2)")
	void canceledRace() {
		RaceCourseInfo course = StubRaceCoursePort.canceled(course(14, null));

		assertThat(course.isAccepting(OPEN_AT.plusDays(1))).isFalse();
		assertThat(course.isBeforeClose(OPEN_AT.plusDays(1))).isFalse();
		assertThat(course.isRefundableOn(REFUND_DEADLINE.plusDays(30))).isTrue();
		assertThat(course(14, null).canceled()).isFalse();
	}

	@Test
	@DisplayName("환불 사유: 환불 기한 날까지는 일정이 바뀌었어도 구매자 취소, 그 뒤는 일정 변경, 취소된 대회는 늘 대회 취소 (BUK-74)")
	void refundReason() {
		RaceCourseInfo changed = courseChangedAt(REFUND_DEADLINE.minusDays(1).atTime(9, 0));

		assertThat(changed.refundReasonOn(REFUND_DEADLINE)).isEqualTo(OrderItemRefundReason.BUYER_CANCELED);
		assertThat(changed.refundReasonOn(REFUND_DEADLINE.plusDays(1)))
			.isEqualTo(OrderItemRefundReason.SCHEDULE_CHANGED);
		assertThat(StubRaceCoursePort.canceled(changed).refundReasonOn(REFUND_DEADLINE.plusDays(30)))
			.isEqualTo(OrderItemRefundReason.RACE_CANCELED);
		assertThat(StubRaceCoursePort.canceled(changed).refundReasonOn(REFUND_DEADLINE))
			.isEqualTo(OrderItemRefundReason.RACE_CANCELED);
	}

	private RaceCourseInfo course(int minAge, Integer maxAge) {
		return new RaceCourseInfo(1, 1, RACE_DATE, OPEN_AT, CLOSE_AT, minAge, maxAge, 1, "종목", 50_000, true,
			REFUND_DEADLINE, null);
	}

	private RaceCourseInfo courseChangedAt(LocalDateTime scheduleChangedAt) {
		return new RaceCourseInfo(1, 1, RACE_DATE, OPEN_AT, CLOSE_AT, 14, null, 1, "종목", 50_000, true,
			REFUND_DEADLINE, scheduleChangedAt);
	}
}
