package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;

/**
 * 대회 취소로 되돌린 회수의 상태가 저장되고 다시 읽힌다 (PR #265 리뷰 REV-4)
 * 운영 DB의 ENUM 목록에 취소가 없던 문제는 Flyway SQL(V202611021610)로 고치고, 목록 대조는 PayoutEnumColumnTest가 한다
 */
@DataJpaTest
@ActiveProfiles("test")
class PayoutStatusColumnTest {

	@Autowired
	private EntityManager entityManager;

	@Test
	@DisplayName("TC-REV4-1 상계로 끝난 회수를 대회 취소로 되돌리면 취소로 저장되고 다시 읽어도 회수_취소다")
	void canceledRecoveryIsSavedAndReadBack() {
		PayoutRecovery recovery = PayoutRecovery.scheduleChanged(1, 7, 108, 20_000,
			LocalDateTime.of(2026, 10, 30, 10, 0));
		entityManager.persist(recovery);
		recovery.completeByOffset(LocalDateTime.of(2026, 11, 1, 3, 0));
		recovery.cancelByRaceCancel();
		entityManager.flush();
		entityManager.clear();

		Object saved = entityManager.createNativeQuery("select status from payout_payout_recovery where id = :id")
			.setParameter("id", recovery.getId())
			.getSingleResult();
		assertThat(saved.toString()).isEqualTo("회수_취소");
		assertThat(entityManager.find(PayoutRecovery.class, recovery.getId()).getStatus())
			.isEqualTo(PayoutRecoveryStatus.회수_취소);
	}
}
