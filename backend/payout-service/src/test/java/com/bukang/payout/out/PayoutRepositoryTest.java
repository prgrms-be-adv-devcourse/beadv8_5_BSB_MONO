package com.bukang.payout.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutItem;

import jakarta.persistence.EntityManager;

@DataJpaTest
@ActiveProfiles("test")
class PayoutRepositoryTest {
	private static final BigDecimal FEE_RATE = new BigDecimal("0.0550");
	private static final BusinessDayCalendar CALENDAR = new BusinessDayCalendar();

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	@DisplayName("정산 내역(Payout)을 정산 항목(PayoutItem)과 함께 저장하면, 다시 읽을 때 항목도 함께 나온다")
	void saveWithItems() {
		Payout payout = new Payout(1, 10, YearMonth.of(2026, 9), LocalDateTime.of(2026, 9, 26, 3, 0), CALENDAR);
		payout.addItem(100, 50_000L, FEE_RATE, LocalDate.of(2026, 9, 26));
		payout.addItem(101, 50_000L, FEE_RATE, LocalDate.of(2026, 9, 26));

		payoutRepository.save(payout);
		entityManager.flush();
		entityManager.clear();

		Payout found = payoutRepository.findById(payout.getId()).orElseThrow();
		assertThat(found.getPayoutMonth()).isEqualTo("2026-09");
		assertThat(found.getItems())
			.extracting(PayoutItem::getOrderItemId)
			.containsExactlyInAnyOrder(100, 101);
	}

	@Test
	@DisplayName("같은 판매자 · 대회 · 월의 정산 내역은 두 번 저장할 수 없다 (중복 지급 방지)")
	void rejectDuplicateSellerRaceMonth() {
		payoutRepository.saveAndFlush(
			new Payout(1, 10, YearMonth.of(2026, 9), LocalDateTime.of(2026, 9, 26, 3, 0), CALENDAR));

		Payout duplicate = new Payout(1, 10, YearMonth.of(2026, 9), LocalDateTime.of(2026, 9, 27, 3, 0), CALENDAR);

		assertThatThrownBy(() -> payoutRepository.saveAndFlush(duplicate))
			.isInstanceOf(DataIntegrityViolationException.class);
	}
}
