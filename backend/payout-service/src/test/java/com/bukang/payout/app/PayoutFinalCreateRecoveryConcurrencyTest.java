package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
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

import com.bukang.payout.app.PayoutCreateStepUseCase.CourseSale;
import com.bukang.payout.app.PayoutCreateStepUseCase.FinalPayoutSource;
import com.bukang.payout.app.PayoutCreateStepUseCase.FirstPayoutSource;
import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutAccount;
import com.bukang.payout.domain.PayoutStage;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRecoveryRepository;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.SoldEntry;

/**
 * 회수와 최종 정산 생성이 겹쳐도 판매자 몫이 한 번만 깎이는지 확인한다 (BUK-52, 판매자 × 대회의 1차 정산 내역 잠금)
 * 대회 A, 판매자 1명, 참가권 5만 원 × 10장(101~110). 108번이 환불되면 정상 케이스의 판매자 총 수령은 425,250원이다
 * (PayoutLedgerScenarioTest와 같은 수). 잠금이 없으면 1차 정산금이 두 번 빠져 405,250원이 된다
 * 테스트 전체를 감싸는 트랜잭션이 있으면 스레드끼리 커밋을 볼 수 없으므로 끄고, 진짜 프록시 빈을 쓴다
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({PayoutLedgerUseCase.class, PayoutCreateStepUseCase.class, PayoutHoldUseCase.class,
	BusinessDayCalendar.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PayoutFinalCreateRecoveryConcurrencyTest {
	private static final long SELLER_ID = 1;
	private static final long RACE_ID = 7;
	private static final long PRICE = 50_000L;
	private static final LocalDate REFUND_DEADLINE = LocalDate.of(2026, 10, 31);
	private static final LocalDate RACE_DATE = LocalDate.of(2026, 12, 13);
	private static final LocalDateTime FINAL_AT = RACE_DATE.plusDays(1).atTime(3, 0);
	// 정상 케이스: 1차 180,000 + 최종 245,250 (갚을 돈 0)
	private static final long NORMAL_TOTAL = 425_250L;
	// 먼저 시작한 쪽이 커밋을 미루는 시간. 이 동안 늦게 온 쪽이 잠금을 기다린다
	private static final long HOLD_LOCK_MILLIS = 500;

	@Autowired
	private PayoutLedgerUseCase ledgerUseCase;

	@Autowired
	private PayoutCreateStepUseCase creator;

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

	// deleteAllInBatch는 cascade가 돌지 않으므로, 정산 내역을 가리키는 줄부터 지운다. 정산 내역은 cascade로 지운다
	@AfterEach
	void cleanUp() {
		payoutItemRepository.deleteAllInBatch();
		payoutRecoveryRepository.deleteAllInBatch();
		payoutHoldRepository.deleteAllInBatch();
		new TransactionTemplate(transactionManager).executeWithoutResult(status ->
			payoutRepository.deleteAll(payoutRepository.findAll()));
	}

	private void createFirst() {
		creator.createFirst(new FirstPayoutSource(SELLER_ID, RACE_ID, "대회 A", true,
			List.of(new CourseSale(RACE_ID, PRICE * 10, 10))), REFUND_DEADLINE,
			REFUND_DEADLINE.plusDays(1).atTime(3, 0));
	}

	private void payFirst() {
		new TransactionTemplate(transactionManager).executeWithoutResult(status ->
			payoutRepository.findFirstBySellerIdAndRaceIdAndPayoutStageOrderByIdAsc(SELLER_ID, RACE_ID,
				PayoutStage.일차_정산).orElseThrow().pay(LocalDate.of(2026, 11, 20).atTime(9, 0)));
	}

	// 108번이 환불됐으므로 최종 판매 내역은 9장이다
	private FinalPayoutSource finalSource() {
		List<SoldEntry> entries = IntStream.rangeClosed(101, 110)
			.filter(id -> id != 108)
			.mapToObj(id -> new SoldEntry(id, SELLER_ID, RACE_ID, RACE_ID, RACE_DATE, PRICE))
			.toList();
		return new FinalPayoutSource(SELLER_ID, RACE_ID, "대회 A", true, entries);
	}

	// 판매자가 결국 받는 돈 = 정산 내역 지급액 합 − 아직 갚지 않은 돈
	private long sellerTotal() {
		long payouts = payoutRepository.findByRaceId(RACE_ID).stream().mapToLong(Payout::getPayoutAmount).sum();
		return payouts - payoutItemRepository.sumBalance(SELLER_ID, RACE_ID, PayoutAccount.갚을_돈);
	}

	// 회수가 트랜잭션을 연 채 커밋을 미루는 동안 최종 정산 생성이 들어온다
	private void createFinalWhileRecoveryIsOpen() throws Exception {
		CountDownLatch recovered = new CountDownLatch(1);
		List<Throwable> errors = new CopyOnWriteArrayList<>();
		CompletableFuture<Void> recovery = CompletableFuture.runAsync(() -> {
			try {
				new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
					ledgerUseCase.recoverScheduleChange(SELLER_ID, RACE_ID, RACE_ID, 108, PRICE,
						LocalDate.of(2026, 12, 14).atTime(2, 59));
					recovered.countDown();
					sleep(HOLD_LOCK_MILLIS);
				});
			} catch (RuntimeException e) {
				errors.add(e);
				recovered.countDown();
			}
		});
		assertThat(recovered.await(5, TimeUnit.SECONDS)).isTrue();
		CompletableFuture<Void> finalCreation = CompletableFuture.runAsync(() -> {
			try {
				creator.createFinal(finalSource(), RACE_DATE, FINAL_AT);
			} catch (RuntimeException e) {
				errors.add(e);
			}
		});
		CompletableFuture.allOf(recovery, finalCreation).get(10, TimeUnit.SECONDS);

		assertThat(errors).isEmpty();
	}

	@Test
	@DisplayName("1차 지급 전: 회수가 열려 있는 동안 최종 정산을 만들어도 판매자 총 수령은 정상 케이스와 같다")
	void overlapBeforeFirstPay() throws Exception {
		createFirst();

		createFinalWhileRecoveryIsOpen();

		assertThat(sellerTotal()).isEqualTo(NORMAL_TOTAL);
	}

	@Test
	@DisplayName("1차 지급 완료: 회수가 열려 있는 동안 최종 정산을 만들어도 판매자 총 수령은 정상 케이스와 같다")
	void overlapAfterFirstPay() throws Exception {
		createFirst();
		payFirst();

		createFinalWhileRecoveryIsOpen();

		assertThat(sellerTotal()).isEqualTo(NORMAL_TOTAL);
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
