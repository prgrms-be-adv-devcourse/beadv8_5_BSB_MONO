package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.shared.order.event.OrderItemRefundReason;
import com.bukang.common.shared.order.event.OrderItemRefundedEvent;
import com.bukang.payout.app.PayoutCreateStepUseCase.CourseSale;
import com.bukang.payout.app.PayoutCreateStepUseCase.FirstPayoutSource;
import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.PayoutAccount;
import com.bukang.payout.domain.PayoutRecovery;
import com.bukang.payout.domain.PayoutRecoveryStatus;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRecoveryRepository;
import com.bukang.payout.out.PayoutRepository;

/**
 * 대회 취소 처리와 대회 취소 환불 회수가 겹쳐도 판매자에게 받지 않은 돈을 청구하지 않는지 확인한다 (BUK-76)
 * 대회 A, 판매자 1명, 참가권 5만 원 × 10장. 1차 정산(200,000원)은 지급 전이다
 * 대회 취소가 1차를 닫으면 판매자가 받은 돈이 없으므로, 어느 쪽이 먼저 잠그든 갚을 돈은 0원이어야 한다
 * 잠금이 없으면 대회 취소의 회수 되돌리기가 아직 커밋되지 않은 회수를 못 보고 넘어가 20,000원이 청구로 남는다
 * 테스트 전체를 감싸는 트랜잭션이 있으면 스레드끼리 커밋을 볼 수 없으므로 끄고, 진짜 프록시 빈을 쓴다
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({PayoutLedgerUseCase.class, PayoutCreateStepUseCase.class, PayoutHoldUseCase.class,
	PayoutRefundUseCase.class, PayoutRaceCancelUseCase.class, BusinessDayCalendar.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PayoutRaceCancelRefundConcurrencyTest {
	private static final long SELLER_ID = 1;
	private static final long RACE_ID = 7;
	private static final long PRICE = 50_000L;
	private static final long ORDER_ITEM_ID = 108;
	private static final LocalDate REFUND_DEADLINE = LocalDate.of(2026, 10, 31);
	private static final LocalDateTime CANCELED_AT = LocalDateTime.of(2026, 11, 10, 10, 0);
	// 먼저 시작한 쪽이 커밋을 미루는 시간. 이 동안 늦게 온 쪽이 잠금을 기다린다
	private static final long HOLD_LOCK_MILLIS = 500;

	@Autowired
	private PayoutCreateStepUseCase creator;

	@Autowired
	private PayoutRefundUseCase refundUseCase;

	@Autowired
	private PayoutRaceCancelUseCase raceCancelUseCase;

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private PayoutItemRepository payoutItemRepository;

	@Autowired
	private PayoutRecoveryRepository payoutRecoveryRepository;

	@Autowired
	private PayoutHoldRepository payoutHoldRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@BeforeEach
	void createFirst() {
		creator.createFirst(new FirstPayoutSource(SELLER_ID, RACE_ID, "대회 A", true,
			List.of(new CourseSale(RACE_ID, PRICE * 10, 10))), REFUND_DEADLINE,
			REFUND_DEADLINE.plusDays(1).atTime(3, 0));
	}

	// deleteAllInBatch는 cascade가 돌지 않으므로, 정산 내역을 가리키는 줄부터 지운다. 정산 내역은 cascade로 지운다
	@AfterEach
	void cleanUp() {
		payoutItemRepository.deleteAllInBatch();
		payoutRecoveryRepository.deleteAllInBatch();
		payoutHoldRepository.deleteAllInBatch();
		new TransactionTemplate(transactionManager).executeWithoutResult(status ->
			payoutRepository.deleteAll(payoutRepository.findAll()));
	}

	private void recordRefund() {
		refundUseCase.recordRefund(new OrderItemRefundedEvent(ORDER_ITEM_ID, RACE_ID, RACE_ID, PRICE, CANCELED_AT,
			OrderItemRefundReason.RACE_CANCELED, 3), CANCELED_AT);
	}

	private void cancelRace() {
		raceCancelUseCase.cancelRace(RACE_ID, CANCELED_AT);
	}

	// first가 트랜잭션을 연 채 커밋을 미루는 동안 second가 들어온다
	private void runOverlapped(Runnable first, Runnable second) throws Exception {
		CountDownLatch started = new CountDownLatch(1);
		List<Throwable> errors = new CopyOnWriteArrayList<>();
		CompletableFuture<Void> firstRun = CompletableFuture.runAsync(() -> {
			try {
				new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
					first.run();
					started.countDown();
					sleep(HOLD_LOCK_MILLIS);
				});
			} catch (RuntimeException e) {
				errors.add(e);
				started.countDown();
			}
		});
		assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
		CompletableFuture<Void> secondRun = CompletableFuture.runAsync(() -> {
			try {
				second.run();
			} catch (RuntimeException e) {
				errors.add(e);
			}
		});
		CompletableFuture.allOf(firstRun, secondRun).get(10, TimeUnit.SECONDS);

		assertThat(errors).isEmpty();
	}

	private long receivable() {
		return payoutItemRepository.sumBalance(SELLER_ID, RACE_ID, PayoutAccount.갚을_돈);
	}

	@Test
	@DisplayName("환불 회수가 열려 있는 동안 대회 취소가 오면, 대회 취소가 기다렸다가 그 회수까지 되돌려 갚을 돈이 0원이다")
	void raceCancelWaitsForOpenRecovery() throws Exception {
		runOverlapped(this::recordRefund, this::cancelRace);

		assertThat(payoutRecoveryRepository.findAll()).extracting(PayoutRecovery::getStatus)
			.containsExactly(PayoutRecoveryStatus.회수_취소);
		assertThat(receivable()).isZero();
	}

	@Test
	@DisplayName("대회 취소가 열려 있는 동안 환불 알림이 오면, 환불이 기다렸다가 닫힌 1차를 보고 회수를 만들지 않는다")
	void refundWaitsForOpenRaceCancel() throws Exception {
		runOverlapped(this::cancelRace, this::recordRefund);

		assertThat(payoutRecoveryRepository.findAll()).isEmpty();
		assertThat(receivable()).isZero();
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}
}
