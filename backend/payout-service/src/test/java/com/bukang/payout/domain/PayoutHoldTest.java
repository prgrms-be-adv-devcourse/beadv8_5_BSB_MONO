package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.bukang.payout.domain.exception.PayoutHoldConflictException;

class PayoutHoldTest {
	private static final BusinessDayCalendar CALENDAR = new BusinessDayCalendar();
	private static final long ADMIN_ID = 99;
	private static final LocalDateTime HELD_AT = LocalDateTime.of(2026, 10, 12, 10, 0);

	// 대회일 10/17 토: 지급 예정일 10/19 월
	private Payout newPayout() {
		return finalPayout(LocalDate.of(2026, 10, 17));
	}

	// 최종 정산의 지급 예정일은 대회일 + 2일이다
	private Payout finalPayout(LocalDate raceDate) {
		return Payout.finalStage(1, 10, "테스트 대회", raceDate, raceDate.plusDays(1).atTime(3, 0), CALENDAR);
	}

	// 1차 정산의 지급 예정일은 환불 마감일 + 2일이다
	private Payout firstPayout(LocalDate refundDeadline) {
		return Payout.first(1, 10, "테스트 대회", refundDeadline, refundDeadline.plusDays(1).atTime(3, 0), CALENDAR);
	}

	private PayoutHold hold(Payout payout) {
		return payout.hold(PayoutHoldReason.ACCOUNT_NOT_REGISTERED, "계좌 확인 중", ADMIN_ID, HELD_AT);
	}

	@Test
	@DisplayName("정산 예정 상태에 보류를 걸면 정산 내역은 HELD, 보류는 지급보류 상태로 만들어진다")
	void holdCalculated() {
		Payout payout = newPayout();

		PayoutHold hold = hold(payout);

		assertThat(payout.getStatus()).isEqualTo(PayoutStatus.HELD);
		assertThat(hold.getPayout()).isSameAs(payout);
		assertThat(hold.getStatus()).isEqualTo(PayoutHoldStatus.HELD);
		assertThat(hold.getReasonType()).isEqualTo(PayoutHoldReason.ACCOUNT_NOT_REGISTERED);
		assertThat(hold.getReason()).isEqualTo("계좌 확인 중");
		assertThat(hold.getHeldBy()).isEqualTo(ADMIN_ID);
		assertThat(hold.getHeldAt()).isEqualTo(HELD_AT);
		assertThat(hold.getReleasedAt()).isNull();
		assertThat(hold.getReleasedBy()).isNull();
	}

	@Test
	@DisplayName("이미 보류 중인 정산 내역에는 보류를 또 걸 수 없다 (활성 보류는 하나)")
	void cannotHoldTwice() {
		Payout payout = newPayout();
		hold(payout);

		assertThatThrownBy(() -> hold(payout))
			.isInstanceOf(PayoutHoldConflictException.class)
			.hasMessage("이미 지급보류 중인 정산 내역입니다.");
	}

	@Test
	@DisplayName("지급 완료된 정산 내역에는 보류를 걸 수 없다")
	void cannotHoldPaid() {
		Payout payout = newPayout();
		payout.pay(LocalDateTime.of(2026, 10, 19, 4, 0));

		assertThatThrownBy(() -> hold(payout))
			.isInstanceOf(PayoutHoldConflictException.class)
			.hasMessage("이미 지급 완료된 정산 내역입니다.");
	}

	@Test
	@DisplayName("보류를 풀면 정산 예정으로 돌아가고, 해제 시각과 해제한 회원이 남는다")
	void release() {
		Payout payout = newPayout();
		PayoutHold hold = hold(payout);
		LocalDateTime releasedAt = LocalDateTime.of(2026, 10, 15, 9, 0);

		payout.release(hold, 77, releasedAt, CALENDAR);

		assertThat(payout.getStatus()).isEqualTo(PayoutStatus.CALCULATED);
		assertThat(hold.getStatus()).isEqualTo(PayoutHoldStatus.RELEASED);
		assertThat(hold.getReleasedAt()).isEqualTo(releasedAt);
		assertThat(hold.getReleasedBy()).isEqualTo(77);
	}

	// 환불 마감일 10/15 목의 원래 지급 예정일은 10/17 토다. 2026-10-18 일, 10-23 금, 10-24 토
	// 1차도 최종과 같은 규칙이다: max(원래 지급 예정일, 해제일 + 1일) (10/28 결정)
	@ParameterizedTest(name = "{0} 해제 → {1} 지급")
	@CsvSource({
		"2026-10-14, 2026-10-17", // 해제일 다음 날(10/15)이 원래 지급 예정일보다 빠르면 앞당기지 않는다 (PRO-128)
		"2026-10-16, 2026-10-17", // 해제일 다음 날이 원래 지급 예정일과 같다
		"2026-10-17, 2026-10-18", // 지급 예정일 당일 해제는 다음 날 (일요일에도 지급)
		"2026-10-20, 2026-10-21", // 지급 예정일이 지났다
		"2026-10-23, 2026-10-24" // 금요일 해제 → 토요일 지급 (영업일 보정 없음)
	})
	@DisplayName("1차 정산의 보류를 풀면 해제일 다음 날에 지급하되, 원래 지급 예정일보다 앞당기지 않는다")
	void releaseReschedulesFirstPayDate(LocalDate releaseDate, LocalDate expectedPayDate) {
		Payout payout = firstPayout(LocalDate.of(2026, 10, 15));
		PayoutHold hold = hold(payout);

		payout.release(hold, ADMIN_ID, releaseDate.atTime(9, 0), CALENDAR);

		assertThat(payout.getScheduledPayDate()).isEqualTo(expectedPayDate);
	}

	// 대회일 10/17 토의 원래 지급 예정일은 10/19 월이다. 2026-10-18 일, 10-23 금, 10-24 토, 10-25 일
	@ParameterizedTest(name = "{0} 해제 → {1} 지급")
	@CsvSource({
		"2026-10-15, 2026-10-19", // 해제일 다음 날(10/16)이 원래 지급 예정일보다 빠르면 앞당기지 않는다
		"2026-10-18, 2026-10-19", // 해제일 다음 날이 원래 지급 예정일과 같다 (일요일 해제)
		"2026-10-19, 2026-10-20", // 지급 예정일 당일 해제는 다음 날
		"2026-10-21, 2026-10-22", // 지급 예정일이 지났다
		"2026-10-23, 2026-10-24", // 금요일 해제 → 토요일 지급 (영업일 보정 없음)
		"2026-10-24, 2026-10-25" // 토요일 해제 → 일요일 지급
	})
	@DisplayName("최종 정산의 보류를 풀면 해제일 다음 날에 지급하되, 원래 지급 예정일보다 앞당기지 않는다")
	void releaseReschedulesFinalPayDate(LocalDate releaseDate, LocalDate expectedPayDate) {
		Payout payout = newPayout();
		PayoutHold hold = hold(payout);

		payout.release(hold, ADMIN_ID, releaseDate.atTime(9, 0), CALENDAR);

		assertThat(payout.getScheduledPayDate()).isEqualTo(expectedPayDate);
	}

	// 해제는 지금 지급 예정일과 비교한다. 해제일은 갈수록 늦어지므로 원래 지급 예정일과 비교한 것과 결과가 같다
	@ParameterizedTest(name = "{0} 해제 → 다시 보류 → {1} 해제 → {2} 지급")
	@CsvSource({
		"2026-10-21, 2026-10-21, 2026-10-22", // 같은 날 두 번 풀어도 하루 더 밀지 않는다
		"2026-10-21, 2026-10-24, 2026-10-25", // 뒤에 푼 날 + 1일
		"2026-10-15, 2026-10-16, 2026-10-19" // 두 번 모두 원래 지급 예정일(10/19) 전에 풀면 그대로
	})
	@DisplayName("두 번 보류하고 풀어도 max(원래 지급 예정일, 마지막 해제일 + 1일)에 지급한다")
	void releaseTwice(LocalDate firstRelease, LocalDate secondRelease, LocalDate expectedPayDate) {
		Payout payout = newPayout();
		payout.release(hold(payout), ADMIN_ID, firstRelease.atTime(9, 0), CALENDAR);

		payout.release(hold(payout), ADMIN_ID, secondRelease.atTime(18, 0), CALENDAR);

		assertThat(payout.getScheduledPayDate()).isEqualTo(expectedPayDate);
	}

	@Test
	@DisplayName("이미 해제된 보류는 다시 풀 수 없다")
	void cannotReleaseTwice() {
		Payout payout = newPayout();
		PayoutHold hold = hold(payout);
		payout.release(hold, ADMIN_ID, LocalDateTime.of(2026, 10, 15, 9, 0), CALENDAR);

		assertThatThrownBy(() -> payout.release(hold, ADMIN_ID, LocalDateTime.of(2026, 10, 16, 9, 0), CALENDAR))
			.isInstanceOf(PayoutHoldConflictException.class)
			.hasMessage("이미 해제된 지급보류입니다.");
	}

	// "다른 정산 내역의 보류로는 풀 수 없다"는 ID로 비교하므로, 저장해 ID가 생기는 PayoutHoldUseCaseTest에서 검사한다
}
