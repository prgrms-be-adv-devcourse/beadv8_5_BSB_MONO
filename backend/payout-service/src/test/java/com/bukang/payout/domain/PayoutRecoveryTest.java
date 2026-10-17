package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.payout.domain.exception.PayoutRecoveryConflictException;

class PayoutRecoveryTest {
	private static final int ADMIN_ID = 99;
	private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 10, 15, 14, 0);

	private PayoutRecovery newRecovery() {
		return new PayoutRecovery(1, 10, PayoutRecoveryReason.RACE_CANCELED, 40_000, REQUESTED_AT);
	}

	@Test
	@DisplayName("회수를 만들면 회수 요청 상태이고, 완료하면 확인한 관리자와 일시가 남는다")
	void complete() {
		PayoutRecovery recovery = newRecovery();
		assertThat(recovery.getStatus()).isEqualTo(PayoutRecoveryStatus.REQUESTED);

		recovery.complete(ADMIN_ID, REQUESTED_AT.plusDays(5));

		assertThat(recovery.getStatus()).isEqualTo(PayoutRecoveryStatus.COMPLETED);
		assertThat(recovery.getCompletedBy()).isEqualTo(ADMIN_ID);
		assertThat(recovery.getCompletedAt()).isEqualTo(REQUESTED_AT.plusDays(5));
	}

	@Test
	@DisplayName("이미 완료한 회수는 다시 완료할 수 없다")
	void cannotCompleteTwice() {
		PayoutRecovery recovery = newRecovery();
		recovery.complete(ADMIN_ID, REQUESTED_AT.plusDays(5));

		assertThatThrownBy(() -> recovery.complete(ADMIN_ID, REQUESTED_AT.plusDays(6)))
			.isInstanceOf(PayoutRecoveryConflictException.class)
			.hasMessage("이미 완료된 회수입니다.");
	}

	@Test
	@DisplayName("회수액이 0원 이하면 만들 수 없다")
	void amountMustBePositive() {
		assertThatThrownBy(() -> new PayoutRecovery(1, 10, PayoutRecoveryReason.RACE_CANCELED, 0, REQUESTED_AT))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("회수 키는 대회 취소면 대회로, 일정 변경이면 참가권으로 만든다")
	void recoveryKey() {
		assertThat(newRecovery().getRecoveryKey()).isEqualTo("R10:RACE_CANCELED");
		assertThat(PayoutRecovery.scheduleChanged(1, 10, 101, 20_000, REQUESTED_AT).getRecoveryKey())
			.isEqualTo("O101:SCHEDULE_CHANGED");
	}

	@Test
	@DisplayName("참가권 없는 일정 변경 회수는 만들 수 없다")
	void scheduleChangedNeedsTicket() {
		assertThatThrownBy(() -> new PayoutRecovery(1, 10, PayoutRecoveryReason.SCHEDULE_CHANGED, 20_000, REQUESTED_AT))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
