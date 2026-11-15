package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;

import com.bukang.common.shared.order.event.OrderItemRefundReason;
import com.bukang.common.shared.order.event.OrderItemRefundedEvent;
import com.bukang.payout.app.PayoutCreateStepUseCase.CourseSale;
import com.bukang.payout.app.PayoutCreateStepUseCase.FinalPayoutSource;
import com.bukang.payout.app.PayoutCreateStepUseCase.FirstPayoutSource;
import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutAccount;
import com.bukang.payout.domain.PayoutRecovery;
import com.bukang.payout.domain.PayoutRecoveryReason;
import com.bukang.payout.domain.PayoutRecoveryStatus;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.domain.exception.PayoutRecoveryConflictException;
import com.bukang.payout.out.PayoutCreateFailureRepository;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRecoveryRepository;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.SoldEntry;

import jakarta.persistence.EntityManager;

/**
 * 참가권 환불 알림을 받아 회수를 남긴다 (선 환불 후 회수 R1, AC-RECOV)
 * 대회 7, 판매자 1, 참가권 5만 원 × 10장. 1장의 1차 몫 20,000원
 */
@DataJpaTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class PayoutRefundUseCaseTest {
	private static final long SELLER_ID = 1;
	private static final long RACE_ID = 7;
	private static final long PRICE = 50_000L;
	private static final LocalDate REFUND_DEADLINE = LocalDate.of(2026, 10, 31);
	private static final LocalDateTime REFUNDED_AT = LocalDateTime.of(2026, 11, 26, 10, 0);
	private static final LocalDate RACE_DATE = LocalDate.of(2026, 11, 28);
	private static final long BUYER_ID = 3;

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private PayoutItemRepository payoutItemRepository;

	@Autowired
	private PayoutHoldRepository payoutHoldRepository;

	@Autowired
	private PayoutRecoveryRepository payoutRecoveryRepository;

	@Autowired
	private PayoutCreateFailureRepository payoutCreateFailureRepository;

	@Autowired
	private EntityManager entityManager;

	private PayoutCreateStepUseCase creator;
	private PayoutRefundUseCase refundUseCase;
	private PayoutRaceCancelUseCase raceCancelUseCase;
	private PayoutLedgerUseCase ledgerUseCase;
	private PayoutPayStepUseCase payStepUseCase;

	@BeforeEach
	void setUp() {
		BusinessDayCalendar calendar = new BusinessDayCalendar();
		ledgerUseCase = new PayoutLedgerUseCase(payoutRepository, payoutItemRepository, payoutRecoveryRepository);
		creator = new PayoutCreateStepUseCase(payoutRepository, calendar, ledgerUseCase,
			new PayoutHoldUseCase(payoutRepository, payoutHoldRepository, calendar));
		refundUseCase = new PayoutRefundUseCase(payoutRepository, ledgerUseCase);
		raceCancelUseCase = new PayoutRaceCancelUseCase(payoutRepository, ledgerUseCase);
		payStepUseCase = new PayoutPayStepUseCase(payoutRepository, raceCancelUseCase, payoutCreateFailureRepository);
	}

	// 대회 취소로 주문이 환불한 참가권마다 환불 알림을 받는다 (BUK-76)
	private void raceCancelRefunds(long from, long to) {
		for (long orderItemId = from; orderItemId <= to; orderItemId++) {
			refundUseCase.recordRefund(refunded(orderItemId, PRICE, OrderItemRefundReason.RACE_CANCELED), REFUNDED_AT);
		}
	}

	private long balance(PayoutAccount account) {
		entityManager.flush();
		return ledgerUseCase.balance(SELLER_ID, RACE_ID, account);
	}

	private void createFirst() {
		creator.createFirst(new FirstPayoutSource(SELLER_ID, RACE_ID, "대회 A", true,
			List.of(new CourseSale(RACE_ID, PRICE * 10, 10))), REFUND_DEADLINE,
			REFUND_DEADLINE.plusDays(1).atTime(3, 0));
	}

	private OrderItemRefundedEvent refunded(long orderItemId, long price) {
		return refunded(orderItemId, price, OrderItemRefundReason.SCHEDULE_CHANGED);
	}

	private OrderItemRefundedEvent refunded(long orderItemId, long price, OrderItemRefundReason reason) {
		return new OrderItemRefundedEvent(orderItemId, RACE_ID, RACE_ID, price, REFUNDED_AT, reason, BUYER_ID);
	}

	private Payout first() {
		return payoutRepository.findFirstBySellerIdAndRaceIdAndPayoutStageOrderByIdAsc(SELLER_ID, RACE_ID,
			PayoutStage.일차_정산).orElseThrow();
	}

	private List<PayoutRecovery> recoveries() {
		entityManager.flush();
		entityManager.clear();
		return payoutRecoveryRepository.findAll();
	}

	@Test
	@DisplayName("TC-RECOV-1-1 1차 정산이 있는 대회의 참가권이 환불되면 그 참가권의 회수가 20,000원으로 하나 생긴다")
	void refundCreatesRecovery() {
		createFirst();

		refundUseCase.recordRefund(refunded(108, PRICE), REFUNDED_AT);

		assertThat(recoveries())
			.extracting(PayoutRecovery::getOrderItemId, PayoutRecovery::getAmount, PayoutRecovery::getSellerId)
			.containsExactly(tuple(108L, 20_000L, SELLER_ID));
	}

	@Test
	@DisplayName("TC-RECOV-1-2 가격 50,001원 참가권이 환불되면 회수는 원 미만을 버린 20,000원이다")
	void refundAmountRoundsDown() {
		createFirst();

		refundUseCase.recordRefund(refunded(108, 50_001L), REFUNDED_AT);

		assertThat(recoveries()).extracting(PayoutRecovery::getAmount).containsExactly(20_000L);
	}

	@Test
	@DisplayName("TC-RECOV-1-3 가격 33,334원 참가권 3장이 환불되면 장마다 버린 뒤 더해 회수 합은 39,999원이다")
	void refundAmountsSumAfterRoundingEachEntry() {
		createFirst();

		for (long orderItemId = 101; orderItemId <= 103; orderItemId++) {
			refundUseCase.recordRefund(refunded(orderItemId, 33_334L), REFUNDED_AT);
		}

		assertThat(recoveries()).extracting(PayoutRecovery::getAmount).containsExactly(13_333L, 13_333L, 13_333L);
	}

	@Test
	@DisplayName("TC-RECOV-2-1 같은 참가권 환불 알림이 다시 와도 오류 없이 회수는 하나다")
	void sameRefundTwiceKeepsOneRecovery() {
		createFirst();

		refundUseCase.recordRefund(refunded(108, PRICE), REFUNDED_AT);
		refundUseCase.recordRefund(refunded(108, PRICE), REFUNDED_AT.plusMinutes(1));

		assertThat(recoveries()).hasSize(1)
			.extracting(PayoutRecovery::getStatus).containsExactly(PayoutRecoveryStatus.회수_완료);
	}

	@Test
	@DisplayName("TC-RECOV-3-1 1차 정산이 없는 대회의 환불 알림은 회수와 원장 줄을 남기지 않는다")
	void refundWithoutFirstIsIgnored() {
		refundUseCase.recordRefund(refunded(108, PRICE), REFUNDED_AT);

		assertThat(recoveries()).isEmpty();
		assertThat(payoutItemRepository.findAll()).isEmpty();
	}

	@Test
	@DisplayName("TC-REV1-1 1차 지급 뒤 대회 취소로 101~110번 환불 알림 10개가 오면 참가권마다 대회 취소 회수 20,000원, 합 200,000원이 생긴다")
	void raceCancelRefundsAreRecoveredPerTicket() {
		createFirst();
		first().pay(REFUNDED_AT.minusDays(20));

		raceCancelUseCase.cancelRace(RACE_ID, REFUNDED_AT);
		raceCancelRefunds(101, 110);

		assertThat(recoveries())
			.extracting(PayoutRecovery::getReasonType, PayoutRecovery::getAmount, PayoutRecovery::getStatus)
			.hasSize(10)
			.containsOnly(tuple(PayoutRecoveryReason.대회_취소, 20_000L, PayoutRecoveryStatus.회수_요청));
		assertThat(recoveries()).extracting(PayoutRecovery::getRecoveryKey).contains("O101:대회_취소");
		assertThat(balance(PayoutAccount.갚을_돈)).isEqualTo(200_000L);
	}

	@Test
	@DisplayName("TC-BUK76-1 환불이 취소 중으로 남은 109·110번은 알림이 오지 않아, 환불된 8장 160,000원만 회수한다")
	void cancelingTicketsAreNotRecovered() {
		createFirst();
		first().pay(REFUNDED_AT.minusDays(20));

		raceCancelUseCase.cancelRace(RACE_ID, REFUNDED_AT);
		raceCancelRefunds(101, 108);

		assertThat(recoveries()).hasSize(8);
		assertThat(balance(PayoutAccount.갚을_돈)).isEqualTo(160_000L);
	}

	@Test
	@DisplayName("TC-BUK76-2 같은 참가권의 대회 취소 환불 알림이 두 번 와도 회수는 하나다")
	void duplicateRaceCancelRefundIsRecoveredOnce() {
		createFirst();
		first().pay(REFUNDED_AT.minusDays(20));

		raceCancelRefunds(108, 108);
		raceCancelRefunds(108, 108);

		assertThat(recoveries()).hasSize(1);
		assertThat(balance(PayoutAccount.갚을_돈)).isEqualTo(20_000L);
	}

	@Test
	@DisplayName("TC-BUK76-3 (M1) 일정 변경으로 이미 회수한 참가권에 대회 취소 환불 알림이 와도 회수를 또 만들지 않는다")
	void scheduleChangeAndRaceCancelDoNotDoubleRecover() {
		createFirst();
		first().pay(REFUNDED_AT.minusDays(20));
		refundUseCase.recordRefund(refunded(108, PRICE, OrderItemRefundReason.SCHEDULE_CHANGED), REFUNDED_AT);

		raceCancelUseCase.cancelRace(RACE_ID, REFUNDED_AT);
		raceCancelRefunds(101, 110);

		// 108번은 일정 변경 회수 하나뿐이고, 나머지 9장은 대회 취소 회수다. 합은 10장 몫 200,000원 그대로다
		assertThat(recoveries()).hasSize(10)
			.filteredOn(recovery -> recovery.getOrderItemId() == 108L)
			.extracting(PayoutRecovery::getReasonType)
			.containsExactly(PayoutRecoveryReason.일정_변경);
		assertThat(balance(PayoutAccount.갚을_돈)).isEqualTo(200_000L);
	}

	@Test
	@DisplayName("TC-BUK76-4 1차 지급 전 대회 취소 메시지가 먼저 오면, 뒤에 온 환불 알림은 1차가 닫혀 회수를 만들지 않는다")
	void refundsAfterFirstClosedAreIgnored() {
		createFirst();

		raceCancelUseCase.cancelRace(RACE_ID, REFUNDED_AT);
		raceCancelRefunds(101, 110);

		assertThat(recoveries()).isEmpty();
		assertThat(balance(PayoutAccount.갚을_돈)).isZero();
	}

	@Test
	@DisplayName("TC-BUK76-5 1차 지급 전 환불 알림이 먼저 와 상계로 끝난 뒤 대회 취소로 1차가 닫히면, 회수를 모두 되돌려 청구는 0원이다")
	void refundsBeforeRaceCancelAreRevertedWhenFirstCloses() {
		createFirst();
		raceCancelRefunds(101, 110);
		assertThat(recoveries()).extracting(PayoutRecovery::getStatus).containsOnly(PayoutRecoveryStatus.회수_완료);

		raceCancelUseCase.cancelRace(RACE_ID, REFUNDED_AT.plusMinutes(1));

		assertThat(recoveries()).extracting(PayoutRecovery::getStatus).containsOnly(PayoutRecoveryStatus.회수_취소);
		assertThat(balance(PayoutAccount.갚을_돈)).isZero();
		assertThat(balance(PayoutAccount.선금)).isZero();
	}

	@Test
	@DisplayName("TC-BUK76-6 1차 지급 중에 환불 알림으로 회수가 요청 상태로 생긴 뒤 지급이 거절되면, 회수를 되돌려 청구는 0원이다")
	void requestedRecoveriesAreRevertedWhenPayIsRejected() {
		createFirst();
		Payout first = first();
		first.startPaying(REFUNDED_AT.minusMinutes(2));
		raceCancelUseCase.cancelRace(RACE_ID, REFUNDED_AT.minusMinutes(1));
		raceCancelRefunds(101, 110);
		assertThat(recoveries()).extracting(PayoutRecovery::getStatus).containsOnly(PayoutRecoveryStatus.회수_요청);

		payStepUseCase.revert(first().getId(), REFUNDED_AT.plusMinutes(1), "캐시 서비스 거절");

		assertThat(recoveries()).extracting(PayoutRecovery::getStatus).containsOnly(PayoutRecoveryStatus.회수_취소);
		assertThat(balance(PayoutAccount.갚을_돈)).isZero();
		assertThat(balance(PayoutAccount.선금)).isZero();
	}

	@Test
	@DisplayName("TC-BUK76-7 1차 지급 중에 생긴 회수는 지급이 성공하면 그대로 남아 200,000원을 청구한다")
	void requestedRecoveriesStayWhenPaySucceeds() {
		createFirst();
		first().startPaying(REFUNDED_AT.minusMinutes(2));
		raceCancelUseCase.cancelRace(RACE_ID, REFUNDED_AT.minusMinutes(1));
		raceCancelRefunds(101, 110);

		payStepUseCase.complete(first().getId(), REFUNDED_AT.plusMinutes(1));

		assertThat(recoveries()).extracting(PayoutRecovery::getStatus).containsOnly(PayoutRecoveryStatus.회수_요청);
		assertThat(balance(PayoutAccount.갚을_돈)).isEqualTo(200_000L);
	}

	@Test
	@DisplayName("TC-REV1-2 1차 20만 원 지급 뒤 일정 변경으로 5장이 환불되면 회수 5건 10만 원, 최종 정산 지급액은 36,250원이다")
	void scheduleChangeRefundsAreRecoveredAndOffset() {
		createFirst();
		first().pay(REFUNDED_AT.minusDays(20));

		for (long orderItemId = 101; orderItemId <= 105; orderItemId++) {
			refundUseCase.recordRefund(refunded(orderItemId, PRICE, OrderItemRefundReason.SCHEDULE_CHANGED),
				REFUNDED_AT);
		}
		assertThat(recoveries()).hasSize(5).extracting(PayoutRecovery::getAmount).containsOnly(20_000L);

		List<SoldEntry> remaining = List.of(106L, 107L, 108L, 109L, 110L).stream()
			.map(orderItemId -> new SoldEntry(orderItemId, SELLER_ID, RACE_ID, RACE_ID, RACE_DATE, PRICE))
			.toList();
		creator.createFinal(new FinalPayoutSource(SELLER_ID, RACE_ID, "대회 A", true, remaining), RACE_DATE,
			RACE_DATE.plusDays(1).atTime(3, 0));
		entityManager.flush();
		entityManager.clear();

		Payout finalPayout = payoutRepository.findFirstBySellerIdAndRaceIdAndPayoutStageOrderByIdAsc(SELLER_ID,
			RACE_ID, PayoutStage.최종_정산).orElseThrow();
		// 250,000(남은 5장) − 13,750(수수료) − 100,000(남은 1차 정산금) − 100,000(상계) = 36,250
		assertThat(finalPayout.getPayoutAmount()).isEqualTo(36_250L);
		assertThat(recoveries()).extracting(PayoutRecovery::getStatus).containsOnly(PayoutRecoveryStatus.회수_완료);
	}

	@Test
	@DisplayName("TC-REV1-3 사유가 빠진 환불 알림은 회수를 남기지 않고 잘못된 메시지로 거절한다 (재시도 없이 실패함으로)")
	void refundWithoutReasonIsRejected() {
		createFirst();

		assertThatThrownBy(() -> refundUseCase.recordRefund(refunded(108, PRICE, null), REFUNDED_AT))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(recoveries()).isEmpty();
	}

	@Test
	@DisplayName("TC-REV3-1 최종 정산을 만든 뒤 일정 변경 환불 알림이 오면 409로 거절하고 회수는 0건, 이유를 담은 ERROR 로그를 1건 남긴다")
	void refundAfterFinalIsRejectedWithLog(CapturedOutput output) {
		createFirst();
		first().pay(REFUNDED_AT.minusDays(20));
		List<SoldEntry> all = List.of(101L, 102L, 103L, 104L, 105L, 106L, 107L, 108L, 109L, 110L).stream()
			.map(orderItemId -> new SoldEntry(orderItemId, SELLER_ID, RACE_ID, RACE_ID, RACE_DATE, PRICE))
			.toList();
		creator.createFinal(new FinalPayoutSource(SELLER_ID, RACE_ID, "대회 A", true, all), RACE_DATE,
			RACE_DATE.plusDays(1).atTime(3, 0));

		assertThatThrownBy(() -> refundUseCase.recordRefund(
			refunded(108, PRICE, OrderItemRefundReason.SCHEDULE_CHANGED), REFUNDED_AT))
			.isInstanceOf(PayoutRecoveryConflictException.class);
		assertThat(recoveries()).isEmpty();
		List<String> logs = output.getAll().lines().filter(line -> line.contains("최종 정산 뒤 환불이라")).toList();
		assertThat(logs).hasSize(1);
		assertThat(logs.getFirst()).contains("ERROR", "sellerId=1", "raceId=7", "orderItemId=108", "price=50000");
	}
}
