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

import com.bukang.payout.domain.PayoutClaim;
import com.bukang.payout.domain.PayoutClaimStatus;
import com.bukang.payout.domain.PayoutItem;
import com.bukang.payout.domain.PayoutRecordType;
import com.bukang.payout.domain.PayoutRecovery;
import com.bukang.payout.out.NoClaimVirtualAccountAdapter;
import com.bukang.payout.out.PayoutClaimRepository;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRecoveryRepository;

/**
 * 같은 청구에 입금 메시지가 동시에 두 번 와도 회수는 한 번만 끝나는지 확인한다 (BUK-134 완료 기준)
 * 테스트 전체를 감싸는 트랜잭션이 있으면 스레드끼리 커밋을 볼 수 없으므로 끄고, 진짜 프록시(PayoutClaimUseCase 빈)를 쓴다
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({PayoutClaimUseCase.class, NoClaimVirtualAccountAdapter.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PayoutClaimDepositConcurrencyTest {
	private static final long SELLER_ID = 1;
	private static final long RACE_ID = 7;
	private static final long AMOUNT = 105_500L;
	private static final LocalDate CLAIM_DATE = LocalDate.of(2026, 12, 14);
	private static final LocalDateTime DEPOSITED_AT = LocalDateTime.of(2026, 12, 18, 14, 30);
	// 먼저 잠근 쪽이 커밋을 미루는 시간. 이 동안 늦게 온 쪽이 같은 청구 행 잠금을 기다린다
	private static final long HOLD_LOCK_MILLIS = 500;

	@Autowired
	private PayoutClaimUseCase claimUseCase;

	@Autowired
	private PayoutClaimRepository payoutClaimRepository;

	@Autowired
	private PayoutItemRepository payoutItemRepository;

	@Autowired
	private PayoutRecoveryRepository payoutRecoveryRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	// deleteAllInBatch는 SQL을 바로 보내 cascade가 돌지 않으므로, 회수를 가리키는 원장 줄부터 지운다
	@AfterEach
	void cleanUp() {
		payoutItemRepository.deleteAllInBatch();
		payoutRecoveryRepository.deleteAllInBatch();
		payoutClaimRepository.deleteAllInBatch();
	}

	// 갚을 돈 105,500원이 남은 판매자 × 대회와 그 금액의 청구를 커밋하고 청구 ID를 돌려준다
	private String openClaim() {
		return new TransactionTemplate(transactionManager).execute(status -> {
			PayoutRecovery recovery = payoutRecoveryRepository.save(PayoutRecovery.scheduleChanged(SELLER_ID, RACE_ID,
				101, AMOUNT, LocalDateTime.of(2026, 11, 26, 10, 0)));
			payoutItemRepository.save(PayoutItem.ofRecovery(recovery, PayoutRecordType.갚을_돈_추가, 101L, RACE_ID,
				AMOUNT));
			PayoutClaim claim = payoutClaimRepository.save(new PayoutClaim(SELLER_ID, RACE_ID, AMOUNT, CLAIM_DATE));
			claim.assignClaimCode(CLAIM_DATE);
			return claim.getClaimCode();
		});
	}

	private long claimDepositLines() {
		return payoutItemRepository.findAll().stream()
			.filter(item -> item.getRecordType() == PayoutRecordType.회수_입금)
			.count();
	}

	@Test
	@DisplayName("TC-DEPOSIT-2-3 첫 메시지가 청구를 잠근 동안 같은 메시지가 오면 기다렸다가 건너뛰고, 회수 입금 줄은 하나다")
	void concurrentSameDepositFinishesRecoveryOnce() throws Exception {
		String claimCode = openClaim();
		CountDownLatch firstLocked = new CountDownLatch(1);
		List<Throwable> errors = new CopyOnWriteArrayList<>();

		// 첫 메시지: 바깥 트랜잭션에 합류해 청구 행 잠금을 쥔 채 커밋을 미룬다
		CompletableFuture<Void> first = CompletableFuture.runAsync(() -> {
			try {
				new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
					claimUseCase.confirmByDeposit(claimCode, AMOUNT, DEPOSITED_AT);
					firstLocked.countDown();
					sleep(HOLD_LOCK_MILLIS);
				});
			} catch (RuntimeException e) {
				errors.add(e);
				firstLocked.countDown();
			}
		});
		// 같은 메시지: 첫 메시지가 잠근 뒤에 들어와 잠금이 풀리기를 기다린다
		assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
		CompletableFuture<Void> second = CompletableFuture.runAsync(() -> {
			try {
				claimUseCase.confirmByDeposit(claimCode, AMOUNT, DEPOSITED_AT);
			} catch (RuntimeException e) {
				errors.add(e);
			}
		});
		CompletableFuture.allOf(first, second).get(10, TimeUnit.SECONDS);

		assertThat(errors).isEmpty();
		assertThat(claimDepositLines()).isEqualTo(1);
		assertThat(payoutClaimRepository.findAll()).extracting(PayoutClaim::getStatus)
			.containsExactly(PayoutClaimStatus.입금_확인);
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
