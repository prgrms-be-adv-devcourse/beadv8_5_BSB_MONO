package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutPayCheckResult;
import com.bukang.payout.domain.PayoutRecovery;
import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.domain.PayoutStatusHistory;
import com.bukang.payout.domain.exception.PayoutPayCheckConflictException;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRecoveryRepository;
import com.bukang.payout.out.PayoutRepository;

import jakarta.persistence.EntityManager;

/**
 * 지급 확인 필요 정산 내역을 관리자가 확인한 결과대로 닫는다 (BUK-174)
 */
@DataJpaTest
@ActiveProfiles("test")
class PayoutPayCheckUseCaseTest {
	private static final long SELLER_ID = 1;
	private static final long RACE_ID = 10;
	private static final long ADMIN_ID = 99;
	private static final BigDecimal FEE_RATE = new BigDecimal("0.0550");
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 23, 10, 0);
	private static final String MEMO = "캐시 정산 이동 기록 확인";

	@Autowired
	private PayoutRepository payoutRepository;
	@Autowired
	private PayoutItemRepository payoutItemRepository;
	@Autowired
	private PayoutRecoveryRepository payoutRecoveryRepository;
	@Autowired
	private EntityManager entityManager;

	private int nextOrderItemId = 1;

	private PayoutRaceCancelUseCase raceCancelUseCase() {
		return new PayoutRaceCancelUseCase(payoutRepository,
			new PayoutLedgerUseCase(payoutRepository, payoutItemRepository, payoutRecoveryRepository));
	}

	private PayoutPayCheckUseCase useCase() {
		return new PayoutPayCheckUseCase(payoutRepository, raceCancelUseCase());
	}

	// 참가권 한 장(price)을 넣은 최종 정산 내역. 유일 조건에 걸리지 않게 정산 월을 달리한다
	private Payout savePayout(YearMonth month, long price) {
		Payout payout = Payout.finalStage(SELLER_ID, RACE_ID, "테스트 대회", month.atEndOfMonth().minusDays(1),
			month.atEndOfMonth().atTime(3, 0), new BusinessDayCalendar());
		payout.addItem(nextOrderItemId++, price, FEE_RATE, month.atEndOfMonth());
		return payoutRepository.save(payout);
	}

	// 지급 중으로 바꾼 뒤 서로 다른 기준일에 결과를 3번 몰라 지급 확인 필요가 된 정산 내역
	private Payout saveCheckRequired(YearMonth month, long price) {
		Payout payout = savePayout(month, price);
		payout.startPaying(NOW.minusDays(5));
		for (int day = 0; day < Payout.MAX_UNKNOWN_PAY; day++) {
			payout.recordUnknownPay(NOW.toLocalDate().minusDays(3 - day), NOW.minusDays(3 - day));
		}
		return payout;
	}

	private Payout reload(Payout payout) {
		entityManager.flush();
		entityManager.clear();
		return payoutRepository.findById(payout.getId()).orElseThrow();
	}

	@Test
	@DisplayName("지급됨으로 닫으면 지급 완료가 되고, 확인한 시각 · 관리자 · 메모가 남는다")
	void paidBecomesPaid() {
		Payout payout = saveCheckRequired(YearMonth.of(2026, 9), 50_000);

		useCase().close(payout.getId(), PayoutPayCheckResult.지급됨, ADMIN_ID, MEMO, NOW);

		Payout closed = reload(payout);
		assertThat(closed.getStatus()).isEqualTo(PayoutStatus.지급_완료);
		assertThat(closed.getPaidAt()).isEqualTo(NOW);
		assertThat(closed.getStatusHistories()).last()
			.extracting(PayoutStatusHistory::getChangedBy, PayoutStatusHistory::getReason)
			.containsExactly(ADMIN_ID, MEMO);
	}

	@Test
	@DisplayName("지급 안 됨으로 닫으면 지급 예정으로 돌아가 다음 지급 배치가 다시 지급하고, 결과 모름 횟수는 0이 된다")
	void notPaidBecomesCalculated() {
		Payout payout = saveCheckRequired(YearMonth.of(2026, 9), 50_000);

		useCase().close(payout.getId(), PayoutPayCheckResult.지급_안_됨, ADMIN_ID, MEMO, NOW);

		Payout closed = reload(payout);
		assertThat(closed.getStatus()).isEqualTo(PayoutStatus.지급_예정);
		assertThat(closed.getUnknownPayCount()).isZero();
	}

	@Test
	@DisplayName("대회 취소 표시가 있는 건을 지급됨으로 닫으면, 미뤄 둔 대회 취소 회수를 이때 만든다")
	void paidAfterRaceCancelCreatesRecovery() {
		Payout payout = saveCheckRequired(YearMonth.of(2026, 9), 50_000);
		raceCancelUseCase().cancelRace(RACE_ID, NOW.minusHours(1));
		assertThat(payoutRecoveryRepository.count()).isZero();

		useCase().close(payout.getId(), PayoutPayCheckResult.지급됨, ADMIN_ID, MEMO, NOW);

		assertThat(reload(payout).getStatus()).isEqualTo(PayoutStatus.지급_완료);
		// 지급액: 50,000 − 2,750(5.5%) = 47,250
		assertThat(payoutRecoveryRepository.findAll()).singleElement()
			.extracting(PayoutRecovery::getAmount).isEqualTo(47_250L);
	}

	@Test
	@DisplayName("대회 취소 표시가 있는 건을 지급 안 됨으로 닫으면, 지급 예정으로 되돌리지 않고 취소로 닫는다")
	void notPaidAfterRaceCancelIsCanceled() {
		Payout payout = saveCheckRequired(YearMonth.of(2026, 9), 50_000);
		raceCancelUseCase().cancelRace(RACE_ID, NOW.minusHours(1));

		useCase().close(payout.getId(), PayoutPayCheckResult.지급_안_됨, ADMIN_ID, MEMO, NOW);

		Payout closed = reload(payout);
		assertThat(closed.getStatus()).isEqualTo(PayoutStatus.취소);
		assertThat(closed.getPayoutAmount()).isZero();
	}

	@Test
	@DisplayName("지급 확인 필요가 아닌 정산 내역은 닫을 수 없다 (409)")
	void cannotCloseOtherStatus() {
		Payout payout = savePayout(YearMonth.of(2026, 9), 50_000);

		assertThatThrownBy(() -> useCase().close(payout.getId(), PayoutPayCheckResult.지급됨, ADMIN_ID, MEMO, NOW))
			.isInstanceOf(PayoutPayCheckConflictException.class);
	}
}
