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

import com.bukang.common.global.eventpublisher.EventPublisher;
import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutHoldReason;
import com.bukang.payout.domain.PayoutHoldStatus;
import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.PayoutStatusHistoryRepository;
import com.bukang.payout.support.MySqlContainerTest;

/**
 * 지급보류 걸기 · 풀기가 같은 정산 내역에서 겹쳐도 보류 상태가 맞는지 확인한다 (BUK-242, 11/12 조사 H1)
 * 테스트 전체를 감싸는 트랜잭션이 있으면 스레드끼리 커밋을 볼 수 없으므로 끄고, 진짜 프록시(PayoutHoldUseCase 빈)를 쓴다
 */
@DataJpaTest
@ActiveProfiles("test")
@MySqlContainerTest
@Import({PayoutHoldUseCase.class, EventPublisher.class, BusinessDayCalendar.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PayoutHoldConcurrencyTest {
	private static final long ADMIN_ID = 99;
	private static final LocalDateTime HELD_AT = LocalDateTime.of(2026, 10, 12, 10, 0);
	private static final LocalDateTime LATER = LocalDateTime.of(2026, 10, 13, 10, 0);
	// 먼저 들어간 쪽이 커밋을 미루는 시간. 이 동안 늦게 온 쪽이 같은 정산 내역을 바꾼다
	private static final long HOLD_TX_MILLIS = 500;

	@Autowired
	private PayoutHoldUseCase holdUseCase;

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private PayoutHoldRepository payoutHoldRepository;

	@Autowired
	private PayoutStatusHistoryRepository payoutStatusHistoryRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	// deleteAllInBatch는 cascade가 돌지 않으므로 정산 내역을 가리키는 행부터 지운다
	@AfterEach
	void cleanUp() {
		payoutHoldRepository.deleteAllInBatch();
		payoutStatusHistoryRepository.deleteAllInBatch();
		payoutRepository.deleteAllInBatch();
	}

	// 대회일 10/17 토: 지급 예정일 10/19 월
	private long savePayout() {
		return payoutRepository.save(Payout.finalStage(1, 10, "테스트 대회", LocalDate.of(2026, 10, 17),
			LocalDateTime.of(2026, 10, 18, 3, 0), new BusinessDayCalendar())).getId();
	}

	private long hold(long payoutId, PayoutHoldReason reason) {
		return holdUseCase.hold(payoutId, reason, "확인 중", ADMIN_ID, HELD_AT).getId();
	}

	private long activeHolds(long payoutId) {
		return payoutHoldRepository.findAll().stream()
			.filter(hold -> hold.getStatus() == PayoutHoldStatus.지급보류)
			.filter(hold -> hold.getPayout().getId() == payoutId)
			.count();
	}

	private PayoutStatus statusOf(long payoutId) {
		return payoutRepository.findById(payoutId).orElseThrow().getStatus();
	}

	/**
	 * 먼저 온 쪽은 바깥 트랜잭션에 합류해 일을 마친 뒤 커밋을 미루고, 그동안 늦게 온 쪽을 실행한다
	 * 잠금이 있으면 늦게 온 쪽이 먼저 온 쪽의 커밋을 기다렸다가 바뀐 상태를 보고 처리한다
	 */
	private List<Throwable> runOverlapped(Runnable first, Runnable second) throws Exception {
		CountDownLatch firstDone = new CountDownLatch(1);
		List<Throwable> errors = new CopyOnWriteArrayList<>();
		CompletableFuture<Void> firstTask = CompletableFuture.runAsync(() -> {
			try {
				new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
					first.run();
					firstDone.countDown();
					sleep(HOLD_TX_MILLIS);
				});
			} catch (RuntimeException e) {
				errors.add(e);
				firstDone.countDown();
			}
		});
		assertThat(firstDone.await(5, TimeUnit.SECONDS)).isTrue();
		CompletableFuture<Void> secondTask = CompletableFuture.runAsync(() -> {
			try {
				second.run();
			} catch (RuntimeException e) {
				errors.add(e);
			}
		});
		CompletableFuture.allOf(firstTask, secondTask).get(10, TimeUnit.SECONDS);
		return errors;
	}

	@Test
	@DisplayName("보류 A를 푸는 동안 보류 B를 걸면, B가 살아 있으므로 정산 내역은 지급보류로 남는다")
	void releaseAndHoldOverlapKeepsHeld() throws Exception {
		long payoutId = savePayout();
		long holdA = hold(payoutId, PayoutHoldReason.정산_계좌_미등록);

		List<Throwable> errors = runOverlapped(
			() -> holdUseCase.release(holdA, ADMIN_ID, LATER),
			() -> hold(payoutId, PayoutHoldReason.대사_불일치));

		assertThat(errors).isEmpty();
		assertThat(activeHolds(payoutId)).isEqualTo(1);
		assertThat(statusOf(payoutId)).isEqualTo(PayoutStatus.지급보류);
	}

	@Test
	@DisplayName("보류 A · B를 동시에 풀면, 남은 보류가 없으므로 정산 내역은 지급 예정으로 돌아간다")
	void releaseBothOverlapReturnsToScheduled() throws Exception {
		long payoutId = savePayout();
		long holdA = hold(payoutId, PayoutHoldReason.정산_계좌_미등록);
		long holdB = hold(payoutId, PayoutHoldReason.대사_불일치);

		List<Throwable> errors = runOverlapped(
			() -> holdUseCase.release(holdA, ADMIN_ID, LATER),
			() -> holdUseCase.release(holdB, ADMIN_ID, LATER));

		assertThat(errors).isEmpty();
		assertThat(activeHolds(payoutId)).isZero();
		assertThat(statusOf(payoutId)).isEqualTo(PayoutStatus.지급_예정);
	}

	@Test
	@DisplayName("관리자가 보류 B를 푸는 동안 계좌 등록이 계좌 미등록 보류를 풀면, 남은 보류가 없으므로 지급 예정으로 돌아간다")
	void adminReleaseAndAccountReleaseOverlapReturnsToScheduled() throws Exception {
		long payoutId = savePayout();
		hold(payoutId, PayoutHoldReason.정산_계좌_미등록);
		long holdB = hold(payoutId, PayoutHoldReason.대사_불일치);

		List<Throwable> errors = runOverlapped(
			() -> holdUseCase.release(holdB, ADMIN_ID, LATER),
			() -> holdUseCase.releaseAccountHolds(1, LATER));

		assertThat(errors).isEmpty();
		assertThat(activeHolds(payoutId)).isZero();
		assertThat(statusOf(payoutId)).isEqualTo(PayoutStatus.지급_예정);
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
