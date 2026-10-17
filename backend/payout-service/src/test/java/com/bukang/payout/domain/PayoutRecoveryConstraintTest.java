package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

/**
 * 회수 중복 방지 (recovery_key 유일, PRO-214)
 * - 대회 취소 회수: 대회당 1건
 * - 일정 변경 회수: 참가권당 1건, 같은 대회에 여러 건 가능
 */
@DataJpaTest
@ActiveProfiles("test")
class PayoutRecoveryConstraintTest {
	private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 10, 15, 14, 0);

	@Autowired
	private EntityManager entityManager;

	private void save(PayoutRecovery recovery) {
		entityManager.persist(recovery);
		entityManager.flush();
	}

	@Test
	@DisplayName("같은 대회의 대회 취소 회수를 두 번 저장하면 실패한다")
	void raceCanceledTwice() {
		save(new PayoutRecovery(1, 10, PayoutRecoveryReason.RACE_CANCELED, 40_000, REQUESTED_AT));

		assertThatThrownBy(() -> save(
			new PayoutRecovery(1, 10, PayoutRecoveryReason.RACE_CANCELED, 40_000, REQUESTED_AT)))
			.isInstanceOf(PersistenceException.class);
	}

	@Test
	@DisplayName("같은 대회라도 참가권이 다르면 일정 변경 회수를 여러 건 저장할 수 있다")
	void scheduleChangedPerTicket() {
		save(PayoutRecovery.scheduleChanged(1, 10, 101, 20_000, REQUESTED_AT));

		assertThatCode(() -> save(PayoutRecovery.scheduleChanged(1, 10, 102, 20_000, REQUESTED_AT)))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("같은 참가권의 일정 변경 회수를 두 번 저장하면 실패한다")
	void scheduleChangedTwice() {
		save(PayoutRecovery.scheduleChanged(1, 10, 101, 20_000, REQUESTED_AT));

		assertThatThrownBy(() -> save(PayoutRecovery.scheduleChanged(1, 10, 101, 20_000, REQUESTED_AT)))
			.isInstanceOf(PersistenceException.class);
	}
}
