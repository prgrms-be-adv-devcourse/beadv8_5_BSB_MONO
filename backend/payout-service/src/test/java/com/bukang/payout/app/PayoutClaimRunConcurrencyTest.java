package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
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
import com.bukang.payout.out.ClaimVirtualAccount;
import com.bukang.payout.out.ClaimVirtualAccountPort;
import com.bukang.payout.out.PayoutClaimRepository;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRecoveryRepository;

/**
 * 청구 배치가 가상계좌를 발급받는 사이에 입금 메시지가 그 청구를 입금 확인으로 커밋해도,
 * 배치가 청구를 '청구 중'으로 되돌려 쓰지 않는지 확인한다 (BUK-88, 11/12 정합성 조사 H3)
 * 스레드끼리 커밋을 봐야 해서 테스트 트랜잭션을 끄고 진짜 프록시를 쓴다
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({PayoutClaimUseCase.class, PayoutClaimRunConcurrencyTest.DepositDuringIssue.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PayoutClaimRunConcurrencyTest {
	private static final long SELLER_ID = 1;
	private static final long RACE_ID = 7;
	private static final long AMOUNT = 105_500L;
	private static final LocalDate CLAIM_DATE = LocalDate.of(2026, 12, 14);
	private static final LocalDateTime DEPOSITED_AT = LocalDateTime.of(2026, 12, 15, 9, 0);

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

	@AfterEach
	void cleanUp() {
		payoutItemRepository.deleteAllInBatch();
		payoutRecoveryRepository.deleteAllInBatch();
		payoutClaimRepository.deleteAllInBatch();
	}

	/**
	 * 발급 요청을 받으면 다른 스레드에서 같은 청구의 입금 메시지를 처리해 커밋까지 기다린 뒤 계좌를 돌려준다.
	 * 배치가 발급을 부르는 동안 청구 행을 잠그고 있으면 입금 쪽이 기다리다 시간 초과로 실패한다
	 */
	@TestConfiguration
	static class DepositDuringIssue {
		@Bean
		ClaimVirtualAccountPort claimVirtualAccountPort(@Lazy PayoutClaimUseCase claimUseCase) {
			return (claimCode, amount, dueDate) -> {
				CompletableFuture.runAsync(() -> claimUseCase.confirmByDeposit(claimCode, amount, DEPOSITED_AT))
					.orTimeout(5, TimeUnit.SECONDS)
					.join();
				return Optional.of(new ClaimVirtualAccount("국민은행", "12345678901234"));
			};
		}
	}

	private void openClaim() {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			PayoutRecovery recovery = payoutRecoveryRepository.save(PayoutRecovery.scheduleChanged(SELLER_ID, RACE_ID,
				101, AMOUNT, LocalDateTime.of(2026, 11, 26, 10, 0)));
			payoutItemRepository.save(PayoutItem.ofRecovery(recovery, PayoutRecordType.갚을_돈_추가, 101L, RACE_ID,
				AMOUNT));
			payoutClaimRepository.save(new PayoutClaim(SELLER_ID, RACE_ID, AMOUNT, CLAIM_DATE))
				.assignClaimCode(CLAIM_DATE);
		});
	}

	@Test
	@DisplayName("발급 중에 입금 확인이 커밋되면 배치는 그 청구를 입금 확인 그대로 두고 판매자 알림도 남기지 않는다")
	void depositDuringIssueKeepsConfirmed() {
		openClaim();

		PayoutClaimUseCase.ClaimRunResult result = claimUseCase.run(CLAIM_DATE.plusDays(1),
			CLAIM_DATE.plusDays(1).atTime(2, 0));

		PayoutClaim claim = payoutClaimRepository.findAll().getFirst();
		assertThat(claim.getStatus()).isEqualTo(PayoutClaimStatus.입금_확인);
		assertThat(claim.getConfirmedAt()).isEqualTo(DEPOSITED_AT);
		assertThat(result.sellerNotices()).isZero();
	}
}
