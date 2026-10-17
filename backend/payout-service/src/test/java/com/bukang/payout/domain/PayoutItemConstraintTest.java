package com.bukang.payout.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

/**
 * 원장 줄 중복 방지 (PRO-214 완료 기준 2)
 * - 참가권 줄: (order_item_id, record_type) 유일. 다른 정산 묶음이어도 막는다
 * - 묶음째 줄: bundle_key 유일
 */
@DataJpaTest
@ActiveProfiles("test")
class PayoutItemConstraintTest {
	private static final BusinessDayCalendar CALENDAR = new BusinessDayCalendar();
	private static final BigDecimal FEE_RATE = new BigDecimal("0.0550");
	private static final LocalDateTime SETTLED_AT = LocalDateTime.of(2026, 12, 14, 3, 0);
	private static final LocalDate TARGET_DATE = LocalDate.of(2026, 12, 14);

	@Autowired
	private EntityManager entityManager;

	private Payout finalPayout(YearMonth month) {
		return Payout.finalStage(1, 7, "대회 A", month, 0L, SETTLED_AT, CALENDAR);
	}

	@Test
	@DisplayName("이미 최종 묶음에 있는 참가권의 판매 줄을 다른 묶음에 넣으면 저장에 실패한다")
	void sameSaleInAnotherPayout() {
		Payout december = finalPayout(YearMonth.of(2026, 12));
		december.addItem(101, 50_000L, FEE_RATE, TARGET_DATE);
		entityManager.persist(december);
		entityManager.flush();

		Payout january = finalPayout(YearMonth.of(2027, 1));
		january.addItem(101, 50_000L, FEE_RATE, TARGET_DATE);

		assertThatThrownBy(() -> {
			entityManager.persist(january);
			entityManager.flush();
		}).isInstanceOf(PersistenceException.class);
	}

	@Test
	@DisplayName("같은 1차 묶음에 선금 줄을 두 번 넣으면 저장에 실패한다 (bundle_key)")
	void advancePaidTwice() {
		Payout first = Payout.first(1, 7, "대회 A", YearMonth.of(2026, 10), SETTLED_AT, CALENDAR);
		first.recordAdvance(7, 50_000L, 1, TARGET_DATE);
		entityManager.persist(first);
		entityManager.flush();

		PayoutItem again = PayoutItem.ofPayout(first, PayoutRecordType.ADVANCE_PAID, null, 7, 20_000L, null,
			TARGET_DATE);

		assertThatThrownBy(() -> {
			entityManager.persist(again);
			entityManager.flush();
		}).isInstanceOf(PersistenceException.class);
	}
}
