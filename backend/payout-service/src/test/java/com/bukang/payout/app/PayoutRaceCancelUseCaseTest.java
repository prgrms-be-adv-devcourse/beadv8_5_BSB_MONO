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
import com.bukang.payout.out.PayoutCreateFailureRepository;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRecoveryRepository;
import com.bukang.payout.out.PayoutRepository;

import jakarta.persistence.EntityManager;

@DataJpaTest
@ActiveProfiles("test")
class PayoutRaceCancelUseCaseTest {
	private static final long SELLER_ID = 1;
	private static final long RACE_ID = 10;
	private static final long OTHER_RACE_ID = 20;
	private static final long ADMIN_ID = 99;
	private static final BigDecimal FEE_RATE = new BigDecimal("0.0550");
	private static final LocalDateTime CANCELED_AT = LocalDateTime.of(2026, 10, 15, 14, 0);

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private PayoutCreateFailureRepository payoutCreateFailureRepository;

	@Autowired
	private PayoutHoldRepository payoutHoldRepository;

	@Autowired
	private PayoutRecoveryRepository payoutRecoveryRepository;

	@Autowired
	private PayoutItemRepository payoutItemRepository;

	@Autowired
	private EntityManager entityManager;

	private PayoutRaceCancelUseCase useCase() {
		return new PayoutRaceCancelUseCase(payoutRepository,
			new PayoutLedgerUseCase(payoutRepository, payoutItemRepository, payoutRecoveryRepository));
	}

	private PayoutPayStepUseCase payStepUseCase() {
		return new PayoutPayStepUseCase(payoutRepository, useCase(), payoutCreateFailureRepository);
	}

	private PayoutHoldUseCase holdUseCase() {
		return new PayoutHoldUseCase(payoutRepository, payoutHoldRepository, new BusinessDayCalendar());
	}

	// 유일 조건 (seller_id, race_id, payout_month)에 걸리지 않도록 정산 월을 달리해 만든다
	// 참가권 한 장(price)을 넣어 지급액이 생기게 한다 (5.5% 수수료를 뗀 금액)
	// 참가권 줄은 (주문 상품, 줄 종류)가 유일하므로 정산 내역마다 다른 주문 상품 ID를 쓴다
	private int nextOrderItemId = 1;

	private Payout savePayout(long raceId, YearMonth month, long price) {
		// 정산 생성일(대회 다음 날)이 그 달 말일이 되도록 대회일을 말일 전날로 둔다
		Payout payout = Payout.finalStage(SELLER_ID, raceId, "테스트 대회", month.atEndOfMonth().minusDays(1),
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
		assertThat(reloaded.getStatus()).isEqualTo(PayoutStatus.취소);
		// 원장 줄을 반대 부호로 되돌려 지급액(줄의 합)은 0, 판매액 칸은 만들 때 그대로 둔다 (PRO-214)
		assertThat(reloaded.getPayoutAmount()).isZero();
		assertThat(reloaded.getSalesAmount()).isEqualTo(50_000);
		PayoutStatusHistory last = reloaded.getStatusHistories().getLast();
		assertThat(last.getFromStatus()).isEqualTo(PayoutStatus.지급_예정);
		assertThat(last.getToStatus()).isEqualTo(PayoutStatus.취소);
		assertThat(last.getChangedBy()).isEqualTo(PayoutHold.SYSTEM_MEMBER_ID);
		assertThat(last.getChangedAt()).isEqualTo(CANCELED_AT);
		assertThat(last.getReason()).isEqualTo("대회 취소");
		assertThat(payoutRecoveryRepository.count()).isZero();
	}

	@Test
	@DisplayName("다른 사유로 보류 중인 정산 내역도 취소로 닫는다. 보류 기록은 그대로 두고 이력에 지급보류 → 취소가 남는다")
	void cancelsHeldPayout() {
		Payout payout = savePayout(RACE_ID, YearMonth.of(2026, 9), 50_000);
		holdUseCase().hold(payout.getId(), PayoutHoldReason.정산_계좌_미등록, null, ADMIN_ID,
			CANCELED_AT.minusDays(1));

		useCase().cancelRace(RACE_ID, CANCELED_AT);

		Payout reloaded = reload(payout);
		assertThat(reloaded.getStatus()).isEqualTo(PayoutStatus.취소);
		assertThat(reloaded.getStatusHistories())
			.extracting(PayoutStatusHistory::getToStatus)
			.containsExactly(PayoutStatus.지급_예정, PayoutStatus.지급보류, PayoutStatus.취소);
		assertThat(payoutHoldRepository.findAll()).singleElement()
			.extracting(PayoutHold::getStatus).isEqualTo(PayoutHoldStatus.지급보류);
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
		assertThat(reload(paid).getStatus()).isEqualTo(PayoutStatus.지급_완료);
		assertThat(reload(otherRace).getStatus()).isEqualTo(PayoutStatus.지급_예정);
		List<PayoutRecovery> recoveries = payoutRecoveryRepository.findAll();
		assertThat(recoveries).hasSize(1);
		PayoutRecovery recovery = recoveries.getFirst();
		assertThat(recovery.getSellerId()).isEqualTo(SELLER_ID);
		assertThat(recovery.getRaceId()).isEqualTo(RACE_ID);
		assertThat(recovery.getReasonType()).isEqualTo(PayoutRecoveryReason.대회_취소);
		// 47,250 + 18,900 (각각 5.5% 수수료를 뗀 지급액)
		assertThat(recovery.getAmount()).isEqualTo(66_150);
		assertThat(recovery.getStatus()).isEqualTo(PayoutRecoveryStatus.회수_요청);
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
	@DisplayName("BUK-166: 지급 확인 필요 정산 내역도 닫지 않고 대회 취소 표시만 남기며, 회수를 만들지 않는다")
	void checkRequiredPayoutWaitsForPayResult() {
		Payout checkRequired = savePayout(RACE_ID, YearMonth.of(2026, 9), 50_000);
		checkRequired.startPaying(CANCELED_AT.minusMinutes(2));
		for (int day = 0; day < Payout.MAX_UNKNOWN_PAY; day++) {
			checkRequired.recordUnknownPay(CANCELED_AT.toLocalDate().minusDays(day), CANCELED_AT.minusMinutes(1));
		}

		PayoutRaceCancelUseCase.CancelResult result = useCase().cancelRace(RACE_ID, CANCELED_AT);

		assertThat(result).isEqualTo(new PayoutRaceCancelUseCase.CancelResult(1, 0, false));
		assertThat(reload(checkRequired).getStatus()).isEqualTo(PayoutStatus.지급_확인_필요);
		assertThat(reload(checkRequired).isRaceCanceled()).isTrue();
		assertThat(payoutRecoveryRepository.count()).isZero();
	}

	@Test
	@DisplayName("지급 중 정산 내역은 닫지 않고, 지급 결과가 나올 때까지 회수를 만들지 않는다 (PRO-253)")
	void payingPayoutWaitsForPayResult() {
		Payout paying = savePayout(RACE_ID, YearMonth.of(2026, 9), 50_000);
		paying.startPaying(CANCELED_AT.minusMinutes(1));

		PayoutRaceCancelUseCase.CancelResult result = useCase().cancelRace(RACE_ID, CANCELED_AT);

		assertThat(result).isEqualTo(new PayoutRaceCancelUseCase.CancelResult(1, 0, false));
		assertThat(reload(paying).getStatus()).isEqualTo(PayoutStatus.지급_중);
		assertThat(payoutRecoveryRepository.count()).isZero();
	}

	@Test
	@DisplayName("대회 취소 뒤 지급 중 정산 내역의 지급이 성공하면 지급 완료로 바꾸고, 그때 지급한 돈만큼 회수를 만든다 (PRO-253)")
	void payingPayoutSucceededAfterRaceCancelCreatesRecovery() {
		Payout paid = savePayout(RACE_ID, YearMonth.of(2026, 8), 20_000);
		paid.pay(CANCELED_AT.minusDays(10));
		Payout paying = savePayout(RACE_ID, YearMonth.of(2026, 9), 50_000);
		paying.startPaying(CANCELED_AT.minusMinutes(1));
		useCase().cancelRace(RACE_ID, CANCELED_AT);
		entityManager.flush();
		entityManager.clear();

		payStepUseCase().complete(paying.getId(), CANCELED_AT.plusMinutes(1));

		assertThat(reload(paying).getStatus()).isEqualTo(PayoutStatus.지급_완료);
		// 지급 완료 두 건의 지급액 합: (20,000 − 1,100) + (50,000 − 2,750) = 18,900 + 47,250 = 66,150
		assertThat(payoutRecoveryRepository.findAll()).singleElement()
			.extracting(PayoutRecovery::getAmount).isEqualTo(66_150L);
	}

	@Test
	@DisplayName("BUK-62: 대회 취소 뒤 지급 중 정산 내역이 409로 지급 불일치가 되면, 돈은 나갔으므로 지급 완료처럼 회수를 만든다")
	void payingPayoutMismatchAfterRaceCancelCreatesRecovery() {
		Payout paid = savePayout(RACE_ID, YearMonth.of(2026, 8), 20_000);
		paid.pay(CANCELED_AT.minusDays(10));
		Payout paying = savePayout(RACE_ID, YearMonth.of(2026, 9), 50_000);
		paying.startPaying(CANCELED_AT.minusMinutes(1));
		useCase().cancelRace(RACE_ID, CANCELED_AT);
		entityManager.flush();
		entityManager.clear();

		payStepUseCase().markMismatch(paying.getId(), CANCELED_AT.plusMinutes(1), "캐시 서비스 금액 불일치 (409)");

		assertThat(reload(paying).getStatus()).isEqualTo(PayoutStatus.지급_불일치);
		// 지급 완료 + 지급 불일치의 원장 지급액 합: 18,900 + 47,250 = 66,150
		assertThat(payoutRecoveryRepository.findAll()).singleElement()
			.extracting(PayoutRecovery::getAmount).isEqualTo(66_150L);
	}

	@Test
	@DisplayName("대회 취소 뒤 지급 중 정산 내역의 지급이 거절되면 정산 예정으로 되돌리지 않고 취소로 닫는다 (PRO-253)")
	void payingPayoutRejectedAfterRaceCancelIsCanceled() {
		Payout paying = savePayout(RACE_ID, YearMonth.of(2026, 9), 50_000);
		paying.startPaying(CANCELED_AT.minusMinutes(1));
		useCase().cancelRace(RACE_ID, CANCELED_AT);
		entityManager.flush();
		entityManager.clear();

		payStepUseCase().revert(paying.getId(), CANCELED_AT.plusMinutes(1), "캐시 서비스 거절");

		Payout reloaded = reload(paying);
		assertThat(reloaded.getStatus()).isEqualTo(PayoutStatus.취소);
		assertThat(reloaded.getPayoutAmount()).isZero();
		// 돈이 나가지 않았으므로 돌려받을 것도 없다
		assertThat(payoutRecoveryRepository.count()).isZero();
	}

	@Test
	@DisplayName("정산 내역이 없는 대회는 아무것도 하지 않는다 (세미는 정산 생성일 전에 취소돼 보통 이 경우다)")
	void noPayouts() {
		PayoutRaceCancelUseCase.CancelResult result = useCase().cancelRace(RACE_ID, CANCELED_AT);

		assertThat(result).isEqualTo(new PayoutRaceCancelUseCase.CancelResult(0, 0, false));
	}
}
