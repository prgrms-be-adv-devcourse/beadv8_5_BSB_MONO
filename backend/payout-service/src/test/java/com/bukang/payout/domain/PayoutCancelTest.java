package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.payout.domain.exception.PayoutHoldConflictException;

class PayoutCancelTest {
	private static final BusinessDayCalendar CALENDAR = new BusinessDayCalendar();
	private static final long ADMIN_ID = 99;
	private static final LocalDateTime SETTLED_AT = LocalDateTime.of(2026, 10, 18, 3, 0);
	private static final LocalDateTime HELD_AT = LocalDateTime.of(2026, 10, 12, 10, 0);
	private static final LocalDateTime CANCELED_AT = LocalDateTime.of(2026, 10, 15, 14, 0);

	private Payout newPayout() {
		return Payout.finalStage(1, 10, "테스트 대회", LocalDate.of(2026, 10, 17), SETTLED_AT, CALENDAR);
	}

	private PayoutHold hold(Payout payout) {
		return payout.hold(PayoutHoldReason.정산_계좌_미등록, null, ADMIN_ID, HELD_AT);
	}

	@Test
	@DisplayName("정산 내역을 만들면 상태 이력 첫 줄에 시스템이 정산 예정으로 만들었다고 남는다")
	void historyOnCreate() {
		Payout payout = newPayout();

		assertThat(payout.getStatusHistories()).singleElement().satisfies(history -> {
			assertThat(history.getFromStatus()).isNull();
			assertThat(history.getToStatus()).isEqualTo(PayoutStatus.지급_예정);
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
		payout.pay(LocalDateTime.of(2026, 10, 19, 4, 0));

		assertThat(payout.getStatusHistories())
			.extracting(PayoutStatusHistory::getFromStatus, PayoutStatusHistory::getToStatus,
				PayoutStatusHistory::getChangedBy)
			.containsExactly(
				tuple(null, PayoutStatus.지급_예정, PayoutHold.SYSTEM_MEMBER_ID),
				tuple(PayoutStatus.지급_예정, PayoutStatus.지급보류, ADMIN_ID),
				tuple(PayoutStatus.지급보류, PayoutStatus.지급_예정, ADMIN_ID),
				tuple(PayoutStatus.지급_예정, PayoutStatus.지급_완료,
					PayoutHold.SYSTEM_MEMBER_ID));
		assertThat(payout.getStatusHistories().get(1).getReason()).isEqualTo("정산 계좌 미등록");
	}

	@Test
	@DisplayName("정산 예정인 정산 내역을 취소하면 취소가 되고 이유가 이력에 남는다")
	void cancelCalculated() {
		Payout payout = newPayout();

		payout.cancel(PayoutHold.SYSTEM_MEMBER_ID, CANCELED_AT, "대회 취소");

		assertThat(payout.getStatus()).isEqualTo(PayoutStatus.취소);
		assertThat(payout.getStatusHistories().getLast().getReason()).isEqualTo("대회 취소");
	}

	@Test
	@DisplayName("보류 중인 정산 내역을 취소하면 상태는 취소, 보류 기록은 지급보류로 남는다")
	void cancelHeld() {
		Payout payout = newPayout();
		PayoutHold hold = hold(payout);

		payout.cancel(PayoutHold.SYSTEM_MEMBER_ID, CANCELED_AT, "대회 취소");

		assertThat(payout.getStatus()).isEqualTo(PayoutStatus.취소);
		assertThat(hold.getStatus()).isEqualTo(PayoutHoldStatus.지급보류);
		assertThat(payout.getStatusHistories().getLast().getFromStatus()).isEqualTo(PayoutStatus.지급보류);
	}

	@Test
	@DisplayName("지급 완료된 정산 내역은 취소할 수 없다 (지급 뒤 대회 취소는 회수)")
	void cannotCancelPaid() {
		Payout payout = newPayout();
		payout.pay(LocalDateTime.of(2026, 10, 19, 4, 0));

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
		assertThat(payout.getStatus()).isEqualTo(PayoutStatus.취소);
		assertThat(hold.getStatus()).isEqualTo(PayoutHoldStatus.지급보류);
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
