package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutItem;
import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.PayoutSourcePort;
import com.bukang.payout.out.SoldEntry;

@DataJpaTest
@ActiveProfiles("test")
class PayoutCreateUseCaseTest {
	private static final LocalDate RACE_DATE = LocalDate.of(2026, 9, 25);
	private static final LocalDate TARGET_DATE = LocalDate.of(2026, 9, 29);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 29, 3, 0);

	@Autowired
	private PayoutRepository payoutRepository;

	// 정산 대상일의 대회일(9/25)로 물어볼 때만 판매된 참가권을 돌려주는 가짜 포트
	private PayoutSourcePort sourceReturning(List<SoldEntry> entries) {
		return raceDate -> raceDate.equals(RACE_DATE) ? entries : List.of();
	}

	private PayoutCreateUseCase useCaseSelling(List<SoldEntry> entries) {
		return new PayoutCreateUseCase(payoutRepository, sourceReturning(entries), new BusinessDayCalendar());
	}

	@Test
	@DisplayName("정산 대상일에 판매자 × 대회별로 정산 내역을 만들고, 참가권마다 정산 항목을 만든다")
	void createsPayoutPerSellerAndRace() {
		PayoutCreateUseCase useCase = useCaseSelling(List.of(
			new SoldEntry(100, 1, 10, 50_000L),
			new SoldEntry(101, 1, 10, 50_000L),
			new SoldEntry(200, 2, 20, 30_000L)
		));

		useCase.run(TARGET_DATE, NOW);

		List<Payout> payouts = payoutRepository.findAll();
		assertThat(payouts).hasSize(2);

		Payout seller1 = payouts.stream().filter(p -> p.getSellerId() == 1).findFirst().orElseThrow();
		assertThat(seller1.getRaceId()).isEqualTo(10);
		assertThat(seller1.getPayoutMonth()).isEqualTo("2026-09");
		assertThat(seller1.getStatus()).isEqualTo(PayoutStatus.CALCULATED);
		assertThat(seller1.getScheduledPayDate()).isEqualTo(LocalDate.of(2026, 10, 20));
		assertThat(seller1.getPayoutAmount()).isEqualTo(94_500L);
		assertThat(seller1.getItems())
			.extracting(PayoutItem::getOrderItemId)
			.containsExactlyInAnyOrder(100, 101);
		assertThat(seller1.getItems())
			.extracting(PayoutItem::getTargetDate)
			.containsOnly(TARGET_DATE);
	}

	@Test
	@DisplayName("정산 ID는 'ST-만든 날-16진수 4자리' 형식이다")
	void assignsPayoutCode() {
		PayoutCreateUseCase useCase = useCaseSelling(List.of(
			new SoldEntry(100, 1, 10, 50_000L)
		));

		useCase.run(TARGET_DATE, NOW);

		Payout payout = payoutRepository.findAll().getFirst();
		assertThat(payout.getPayoutCode()).matches("ST-20260929-[0-9A-F]{4}");
	}

	@Test
	@DisplayName("같은 날 배치를 두 번 돌려도 정산 내역이 중복으로 만들어지지 않는다")
	void runTwiceDoesNotDuplicate() {
		PayoutCreateUseCase useCase = useCaseSelling(List.of(
			new SoldEntry(100, 1, 10, 50_000L)
		));

		useCase.run(TARGET_DATE, NOW);
		useCase.run(TARGET_DATE, NOW.plusHours(1));

		assertThat(payoutRepository.findAll()).hasSize(1);
	}
}
