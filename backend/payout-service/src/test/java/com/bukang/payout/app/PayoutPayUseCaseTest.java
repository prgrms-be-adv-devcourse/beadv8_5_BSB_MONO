package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.out.PayoutRepository;

import jakarta.persistence.EntityManager;

@DataJpaTest
@ActiveProfiles("test")
class PayoutPayUseCaseTest {
	private static final LocalDate PAY_DATE = LocalDate.of(2026, 10, 20);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 4, 0);

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private EntityManager entityManager;

	private PayoutPayUseCase useCase() {
		return new PayoutPayUseCase(payoutRepository);
	}

	// 9월분(2026-09)은 10/20, 10월분(2026-10)은 11/20이 지급 예정일이다
	private Payout savePayout(int sellerId, YearMonth payoutMonth) {
		LocalDateTime settledAt = payoutMonth.atDay(29).atTime(3, 0);
		return payoutRepository.save(new Payout(sellerId, 10, payoutMonth, settledAt, new BusinessDayCalendar()));
	}

	@Test
	@DisplayName("10/20에 9월 정산 대상분이 지급 완료(PAID)로 바뀌고 지급 시각이 남는다")
	void paysPreviousMonthOnPayDate() {
		Payout september = savePayout(1, YearMonth.of(2026, 9));

		useCase().run(PAY_DATE, NOW);

		Payout paid = payoutRepository.findById(september.getId()).orElseThrow();
		assertThat(paid.getStatus()).isEqualTo(PayoutStatus.PAID);
		assertThat(paid.getPaidAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("10월 정산 대상분은 10/20에 지급하지 않는다 (11/20 지급분)")
	void doesNotPayCurrentMonth() {
		Payout october = savePayout(1, YearMonth.of(2026, 10));

		useCase().run(PAY_DATE, NOW);

		assertThat(statusOf(october)).isEqualTo(PayoutStatus.CALCULATED);
	}

	@Test
	@DisplayName("지급 예정일 전날(10/19)에는 9월분도 지급하지 않는다")
	void doesNotPayBeforePayDate() {
		Payout september = savePayout(1, YearMonth.of(2026, 9));

		useCase().run(PAY_DATE.minusDays(1), NOW.minusDays(1));

		assertThat(statusOf(september)).isEqualTo(PayoutStatus.CALCULATED);
	}

	@Test
	@DisplayName("지급보류(HELD)인 정산 내역은 지급 예정일이 지나도 PAID로 바뀌지 않는다")
	void doesNotPayHeld() {
		Payout held = savePayout(1, YearMonth.of(2026, 9));
		// 보류를 거는 기능(payout.hold)은 PRO-26 범위라, 여기서는 상태만 HELD로 바꿔 둔다
		entityManager.createQuery("update Payout p set p.status = :held where p.id = :id")
			.setParameter("held", PayoutStatus.HELD)
			.setParameter("id", held.getId())
			.executeUpdate();
		entityManager.clear();

		useCase().run(PAY_DATE, NOW);

		assertThat(statusOf(held)).isEqualTo(PayoutStatus.HELD);
	}

	@Test
	@DisplayName("같은 날 다시 돌려도 이미 지급한 정산 내역의 지급 시각은 바뀌지 않는다")
	void runTwiceKeepsFirstPaidAt() {
		Payout september = savePayout(1, YearMonth.of(2026, 9));

		useCase().run(PAY_DATE, NOW);
		useCase().run(PAY_DATE, NOW.plusHours(1));

		assertThat(payoutRepository.findById(september.getId()).orElseThrow().getPaidAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("10/20 실행을 놓쳐도 10/21 실행에서 9월분을 지급한다")
	void catchesUpMissedPayDate() {
		Payout september = savePayout(1, YearMonth.of(2026, 9));

		useCase().run(PAY_DATE.plusDays(1), NOW.plusDays(1));

		assertThat(statusOf(september)).isEqualTo(PayoutStatus.PAID);
	}

	private PayoutStatus statusOf(Payout payout) {
		return payoutRepository.findById(payout.getId()).orElseThrow().getStatus();
	}
}
