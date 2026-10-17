package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.bukang.payout.domain.exception.PayoutHoldConflictException;

class PayoutHoldTest {
	private static final BusinessDayCalendar CALENDAR = new BusinessDayCalendar();
	private static final int ADMIN_ID = 99;
	private static final LocalDateTime HELD_AT = LocalDateTime.of(2026, 10, 12, 10, 0);

	// 2026-09: 지급 예정일 10/20
	private Payout newPayout() {
		return newPayout(YearMonth.of(2026, 9));
	}

	// 지급 예정일은 정산 월 다음 달 20일이다 (2026-10 → 11/20)
	private Payout newPayout(YearMonth payoutMonth) {
		return Payout.finalStage(1, 10, "테스트 대회", payoutMonth, 0L, payoutMonth.atDay(26).atTime(3, 0), CALENDAR);
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
		payout.pay(LocalDateTime.of(2026, 10, 20, 4, 0));

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

	// 2026-10-20 화, 2026-11-20 금, 2026-12-20 일 → 12/21 월, 2027-01-20 수
	// 정산 월 2026-09의 원래 지급 예정일은 10/20, 2026-10은 11/20이다
	@ParameterizedTest(name = "정산 월 {0}, {1} 해제 → {2} 지급")
	@CsvSource({
		"2026-09, 2026-10-15, 2026-10-20", // 이번 달 지급일이 아직 안 왔다
		"2026-09, 2026-10-20, 2026-11-20", // 지급일 당일 해제는 다음 달 (payout.md 2-6)
		"2026-09, 2026-10-21, 2026-11-20", // 이번 달 지급일이 지났다
		"2026-09, 2026-12-20, 2026-12-21", // 이번 달 지급일(20일 일요일)이 월요일로 밀려 아직 안 왔다
		"2026-09, 2026-12-21, 2027-01-20", // 밀린 지급일 당일 해제는 다음 달
		"2026-10, 2026-10-15, 2026-11-20" // 원래 지급 예정일(11/20) 전에 풀려도 앞당기지 않는다 (PRO-128)
	})
	@DisplayName("보류를 풀면 지급 예정일을 해제일 뒤 돌아오는 지급일로 다시 정하되, 원래 지급 예정일보다 앞당기지 않는다")
	void releaseReschedulesPayDate(YearMonth payoutMonth, LocalDate releaseDate, LocalDate expectedPayDate) {
		Payout payout = newPayout(payoutMonth);
		PayoutHold hold = hold(payout);

		payout.release(hold, ADMIN_ID, releaseDate.atTime(9, 0), CALENDAR);

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
