package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutHold;
import com.bukang.payout.domain.PayoutHoldReason;
import com.bukang.payout.domain.PayoutHoldStatus;
import com.bukang.payout.domain.PayoutRecovery;
import com.bukang.payout.domain.PayoutRecoveryReason;
import com.bukang.payout.domain.PayoutRecoveryStatus;
import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.domain.PayoutStatusHistory;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRecoveryRepository;
import com.bukang.payout.out.PayoutRepository;

import jakarta.persistence.EntityManager;

@DataJpaTest
@ActiveProfiles("test")
class PayoutRaceCancelUseCaseTest {
	private static final int SELLER_ID = 1;
	private static final int RACE_ID = 10;
	private static final int OTHER_RACE_ID = 20;
	private static final int ADMIN_ID = 99;
	private static final BigDecimal FEE_RATE = new BigDecimal("0.0550");
	private static final LocalDateTime CANCELED_AT = LocalDateTime.of(2026, 10, 15, 14, 0);

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private PayoutHoldRepository payoutHoldRepository;

	@Autowired
	private PayoutRecoveryRepository payoutRecoveryRepository;

	@Autowired
	private PayoutItemRepository payoutItemRepository;

	@Autowired
	private EntityManager entityManager;

	private PayoutRaceCancelUseCase useCase() {
		return new PayoutRaceCancelUseCase(payoutRepository, payoutItemRepository, payoutRecoveryRepository,
			new PayoutLedgerUseCase(payoutRepository, payoutItemRepository, payoutRecoveryRepository));
	}

	private PayoutHoldUseCase holdUseCase() {
		return new PayoutHoldUseCase(payoutRepository, payoutHoldRepository, new BusinessDayCalendar());
	}

	// 유일 조건 (seller_id, race_id, payout_month)에 걸리지 않도록 정산 월을 달리해 만든다
	// 참가권 한 장(price)을 넣어 지급액이 생기게 한다 (5.5% 수수료를 뗀 금액)
	// 참가권 줄은 (주문 상품, 줄 종류)가 유일하므로 정산 내역마다 다른 주문 상품 ID를 쓴다
	private int nextOrderItemId = 1;

	private Payout savePayout(int raceId, YearMonth month, long price) {
		Payout payout = Payout.finalStage(SELLER_ID, raceId, "테스트 대회", month, 0L,
			month.atEndOfMonth().atTime(3, 0), new BusinessDayCalendar());
		if (price > 0) {
			payout.addItem(nextOrderItemId++, price, FEE_RATE, month.atEndOfMonth());
		}
		return payoutRepository.save(payout);
	}

	private Payout reload(Payout payout) {
		entityManager.flush();
		entityManager.clear();
		return payoutRepository.findById(payout.getId()).orElseThrow();
	}

	@Test
	@DisplayName("지급 전(정산 예정) 정산 내역을 취소로 닫고, 상태 이력에 시스템이 대회 취소로 닫았다고 남긴다")
	void cancelsCalculatedPayout() {
		Payout payout = savePayout(RACE_ID, YearMonth.of(2026, 9), 50_000);

		PayoutRaceCancelUseCase.CancelResult result = useCase().cancelRace(RACE_ID, CANCELED_AT);

		assertThat(result).isEqualTo(new PayoutRaceCancelUseCase.CancelResult(1, 1, false));
		Payout reloaded = reload(payout);
		assertThat(reloaded.getStatus()).isEqualTo(PayoutStatus.CANCELED);
		// 원장 줄을 반대 부호로 되돌려 지급액(줄의 합)은 0, 판매액 칸은 만들 때 그대로 둔다 (PRO-214)
		assertThat(reloaded.getPayoutAmount()).isZero();
		assertThat(reloaded.getSalesAmount()).isEqualTo(50_000);
		PayoutStatusHistory last = reloaded.getStatusHistories().getLast();
		assertThat(last.getFromStatus()).isEqualTo(PayoutStatus.CALCULATED);
		assertThat(last.getToStatus()).isEqualTo(PayoutStatus.CANCELED);
		assertThat(last.getChangedBy()).isEqualTo(PayoutHold.SYSTEM_MEMBER_ID);
		assertThat(last.getChangedAt()).isEqualTo(CANCELED_AT);
		assertThat(last.getReason()).isEqualTo("대회 취소");
		assertThat(payoutRecoveryRepository.count()).isZero();
	}

	@Test
	@DisplayName("다른 사유로 보류 중인 정산 내역도 취소로 닫는다. 보류 기록은 그대로 두고 이력에 HELD → CANCELED가 남는다")
	void cancelsHeldPayout() {
		Payout payout = savePayout(RACE_ID, YearMonth.of(2026, 9), 50_000);
		holdUseCase().hold(payout.getId(), PayoutHoldReason.ACCOUNT_NOT_REGISTERED, null, ADMIN_ID,
			CANCELED_AT.minusDays(1));

		useCase().cancelRace(RACE_ID, CANCELED_AT);

		Payout reloaded = reload(payout);
		assertThat(reloaded.getStatus()).isEqualTo(PayoutStatus.CANCELED);
		assertThat(reloaded.getStatusHistories())
			.extracting(PayoutStatusHistory::getToStatus)
			.containsExactly(PayoutStatus.CALCULATED, PayoutStatus.HELD, PayoutStatus.CANCELED);
		assertThat(payoutHoldRepository.findAll()).singleElement()
			.extracting(PayoutHold::getStatus).isEqualTo(PayoutHoldStatus.HELD);
	}

	@Test
	@DisplayName("지급 완료된 정산 내역은 그대로 두고, 지급액 합만큼 회수를 대회당 1건 만든다")
	void createsRecoveryForPaidPayouts() {
		Payout paid = savePayout(RACE_ID, YearMonth.of(2026, 8), 50_000);
		paid.pay(CANCELED_AT.minusDays(10));
		Payout otherPaid = savePayout(RACE_ID, YearMonth.of(2026, 9), 20_000);
		otherPaid.pay(CANCELED_AT.minusDays(5));
		Payout otherRace = savePayout(OTHER_RACE_ID, YearMonth.of(2026, 9), 50_000);

		PayoutRaceCancelUseCase.CancelResult result = useCase().cancelRace(RACE_ID, CANCELED_AT);

		assertThat(result).isEqualTo(new PayoutRaceCancelUseCase.CancelResult(2, 0, true));
		assertThat(reload(paid).getStatus()).isEqualTo(PayoutStatus.PAID);
		assertThat(reload(otherRace).getStatus()).isEqualTo(PayoutStatus.CALCULATED);
		List<PayoutRecovery> recoveries = payoutRecoveryRepository.findAll();
		assertThat(recoveries).hasSize(1);
		PayoutRecovery recovery = recoveries.getFirst();
		assertThat(recovery.getSellerId()).isEqualTo(SELLER_ID);
		assertThat(recovery.getRaceId()).isEqualTo(RACE_ID);
		assertThat(recovery.getReasonType()).isEqualTo(PayoutRecoveryReason.RACE_CANCELED);
		// 47,250 + 18,900 (각각 5.5% 수수료를 뗀 지급액)
		assertThat(recovery.getAmount()).isEqualTo(66_150);
		assertThat(recovery.getStatus()).isEqualTo(PayoutRecoveryStatus.REQUESTED);
		assertThat(recovery.getRequestedAt()).isEqualTo(CANCELED_AT);
	}

	@Test
	@DisplayName("0원으로 지급 완료된 정산 내역만 있으면 돌려받을 돈이 없어 회수를 만들지 않는다")
	void noRecoveryForZeroPaid() {
		Payout zero = savePayout(RACE_ID, YearMonth.of(2026, 9), 0);
		zero.pay(CANCELED_AT.minusDays(1));

		PayoutRaceCancelUseCase.CancelResult result = useCase().cancelRace(RACE_ID, CANCELED_AT);

		assertThat(result.recoveryCreated()).isFalse();
		assertThat(payoutRecoveryRepository.count()).isZero();
	}

	@Test
	@DisplayName("같은 대회 취소 메시지가 다시 와도 이미 닫은 정산 내역은 건너뛰고 회수도 한 번만 만든다")
	void idempotentOnRedelivery() {
		Payout calculated = savePayout(RACE_ID, YearMonth.of(2026, 9), 50_000);
		Payout paid = savePayout(RACE_ID, YearMonth.of(2026, 8), 50_000);
		paid.pay(CANCELED_AT.minusDays(10));
		useCase().cancelRace(RACE_ID, CANCELED_AT);
		entityManager.flush();
		entityManager.clear();

		PayoutRaceCancelUseCase.CancelResult again = useCase().cancelRace(RACE_ID, CANCELED_AT.plusMinutes(1));

		assertThat(again).isEqualTo(new PayoutRaceCancelUseCase.CancelResult(2, 0, false));
		assertThat(reload(calculated).getStatusHistories()).hasSize(2);
		assertThat(payoutRecoveryRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("정산 내역이 없는 대회는 아무것도 하지 않는다 (세미는 정산 대상일 전에 취소돼 보통 이 경우다)")
	void noPayouts() {
		PayoutRaceCancelUseCase.CancelResult result = useCase().cancelRace(RACE_ID, CANCELED_AT);

		assertThat(result).isEqualTo(new PayoutRaceCancelUseCase.CancelResult(0, 0, false));
	}
}
