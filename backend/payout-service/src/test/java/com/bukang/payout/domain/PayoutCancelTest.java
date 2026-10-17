package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDateTime;
import java.time.YearMonth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.payout.domain.exception.PayoutHoldConflictException;

class PayoutCancelTest {
	private static final BusinessDayCalendar CALENDAR = new BusinessDayCalendar();
	private static final int ADMIN_ID = 99;
	private static final LocalDateTime SETTLED_AT = LocalDateTime.of(2026, 9, 26, 3, 0);
	private static final LocalDateTime HELD_AT = LocalDateTime.of(2026, 10, 12, 10, 0);
	private static final LocalDateTime CANCELED_AT = LocalDateTime.of(2026, 10, 15, 14, 0);

	private Payout newPayout() {
		return Payout.finalStage(1, 10, "테스트 대회", YearMonth.of(2026, 9), 0L, SETTLED_AT, CALENDAR);
	}

	private PayoutHold hold(Payout payout) {
		return payout.hold(PayoutHoldReason.ACCOUNT_NOT_REGISTERED, null, ADMIN_ID, HELD_AT);
	}

	@Test
	@DisplayName("정산 내역을 만들면 상태 이력 첫 줄에 시스템이 정산 예정으로 만들었다고 남는다")
	void historyOnCreate() {
		Payout payout = newPayout();

		assertThat(payout.getStatusHistories()).singleElement().satisfies(history -> {
			assertThat(history.getFromStatus()).isNull();
			assertThat(history.getToStatus()).isEqualTo(PayoutStatus.CALCULATED);
			assertThat(history.getChangedBy()).isEqualTo(PayoutHold.SYSTEM_MEMBER_ID);
			assertThat(history.getChangedAt()).isEqualTo(SETTLED_AT);
		});
	}

	@Test
	@DisplayName("보류 · 해제 · 지급도 상태 이력에 누가 언제 바꿨는지 남는다")
	void historyOnHoldReleasePay() {
		Payout payout = newPayout();
		PayoutHold hold = hold(payout);
		payout.release(hold, ADMIN_ID, HELD_AT.plusDays(1), CALENDAR);
		payout.pay(LocalDateTime.of(2026, 11, 20, 4, 0));

		assertThat(payout.getStatusHistories())
			.extracting(PayoutStatusHistory::getFromStatus, PayoutStatusHistory::getToStatus,
				PayoutStatusHistory::getChangedBy)
			.containsExactly(
				tuple(null, PayoutStatus.CALCULATED, PayoutHold.SYSTEM_MEMBER_ID),
				tuple(PayoutStatus.CALCULATED, PayoutStatus.HELD, ADMIN_ID),
				tuple(PayoutStatus.HELD, PayoutStatus.CALCULATED, ADMIN_ID),
				tuple(PayoutStatus.CALCULATED, PayoutStatus.PAID,
					PayoutHold.SYSTEM_MEMBER_ID));
		assertThat(payout.getStatusHistories().get(1).getReason()).isEqualTo("정산 계좌 미등록");
	}

	@Test
	@DisplayName("정산 예정인 정산 내역을 취소하면 CANCELED가 되고 이유가 이력에 남는다")
	void cancelCalculated() {
		Payout payout = newPayout();

		payout.cancel(PayoutHold.SYSTEM_MEMBER_ID, CANCELED_AT, "대회 취소");

		assertThat(payout.getStatus()).isEqualTo(PayoutStatus.CANCELED);
		assertThat(payout.getStatusHistories().getLast().getReason()).isEqualTo("대회 취소");
	}

	@Test
	@DisplayName("보류 중인 정산 내역을 취소하면 상태는 CANCELED, 보류 기록은 HELD로 남는다")
	void cancelHeld() {
		Payout payout = newPayout();
		PayoutHold hold = hold(payout);

		payout.cancel(PayoutHold.SYSTEM_MEMBER_ID, CANCELED_AT, "대회 취소");

		assertThat(payout.getStatus()).isEqualTo(PayoutStatus.CANCELED);
		assertThat(hold.getStatus()).isEqualTo(PayoutHoldStatus.HELD);
		assertThat(payout.getStatusHistories().getLast().getFromStatus()).isEqualTo(PayoutStatus.HELD);
	}

	@Test
	@DisplayName("지급 완료된 정산 내역은 취소할 수 없다 (지급 뒤 대회 취소는 회수)")
	void cannotCancelPaid() {
		Payout payout = newPayout();
		payout.pay(LocalDateTime.of(2026, 10, 20, 4, 0));

		assertThatThrownBy(() -> payout.cancel(PayoutHold.SYSTEM_MEMBER_ID, CANCELED_AT, "대회 취소"))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("취소된 정산 내역에 남은 보류는 풀 수 없다. 풀어도 정산 예정으로 돌아가 지급되는 일을 막는다")
	void cannotReleaseOnCanceled() {
		Payout payout = newPayout();
		PayoutHold hold = hold(payout);
		payout.cancel(PayoutHold.SYSTEM_MEMBER_ID, CANCELED_AT, "대회 취소");

		assertThatThrownBy(() -> payout.release(hold, ADMIN_ID, CANCELED_AT.plusDays(1), CALENDAR))
			.isInstanceOf(PayoutHoldConflictException.class)
			.hasMessage("취소된 정산 내역의 지급보류는 풀 수 없습니다.");
		assertThat(payout.getStatus()).isEqualTo(PayoutStatus.CANCELED);
		assertThat(hold.getStatus()).isEqualTo(PayoutHoldStatus.HELD);
	}

	@Test
	@DisplayName("취소된 정산 내역에는 보류를 걸 수 없다")
	void cannotHoldCanceled() {
		Payout payout = newPayout();
		payout.cancel(PayoutHold.SYSTEM_MEMBER_ID, CANCELED_AT, "대회 취소");

		assertThatThrownBy(() -> hold(payout))
			.isInstanceOf(PayoutHoldConflictException.class)
			.hasMessage("취소된 정산 내역입니다.");
	}
}
