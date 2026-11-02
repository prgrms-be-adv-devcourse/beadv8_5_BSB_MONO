package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.payout.domain.BusinessDayCalendar;
import com.bukang.payout.domain.Payout;
import com.bukang.payout.domain.PayoutHoldReason;
import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.domain.exception.PayoutHoldConflictException;
import com.bukang.payout.out.PayoutHoldRepository;
import com.bukang.payout.out.PayoutItemRepository;
import com.bukang.payout.out.PayoutRepository;
import com.bukang.payout.out.PayoutStatusHistoryRepository;

/**
 * 지급 배치가 정산 내역 한 건마다 트랜잭션을 나누는지 확인한다 (PRO-252)
 * 테스트 전체를 감싸는 트랜잭션이 있으면 지급 중(PAYING)의 커밋이 보이지 않으므로 끄고,
 * 진짜 프록시(PayoutPayStepUseCase 빈)를 쓴다
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({PayoutPayStepUseCase.class, PayoutHoldUseCase.class, BusinessDayCalendar.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PayoutPayTransactionTest {
	private static final LocalDate PAY_DATE = LocalDate.of(2026, 11, 20);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 4, 0);

	@Autowired
	private PayoutRepository payoutRepository;

	@Autowired
	private PayoutItemRepository payoutItemRepository;

	@Autowired
	private PayoutHoldRepository payoutHoldRepository;

	@Autowired
	private PayoutStatusHistoryRepository payoutStatusHistoryRepository;

	@Autowired
	private PayoutPayStepUseCase payoutPayStepUseCase;

	@Autowired
	private PayoutHoldUseCase payoutHoldUseCase;

	@Autowired
	private PlatformTransactionManager transactionManager;

	// deleteAllInBatch는 SQL을 바로 보내 cascade가 돌지 않으므로, 정산 내역을 가리키는 자식 테이블부터 지운다
	@AfterEach
	void cleanUp() {
		payoutHoldRepository.deleteAllInBatch();
		payoutItemRepository.deleteAllInBatch();
		payoutStatusHistoryRepository.deleteAllInBatch();
		payoutRepository.deleteAllInBatch();
	}

	// 10/17 대회분(11/20 지급), 판매 50,000 → 지급액 47,250 · 수수료 2,750
	private Payout savePayoutWithSale() {
		Payout payout = Payout.finalStage(1, 10, "테스트 대회", LocalDate.of(2026, 10, 17),
			LocalDateTime.of(2026, 10, 18, 3, 0), new BusinessDayCalendar());
		payout.addItem(100, 50_000L, new BigDecimal("0.0550"), LocalDate.of(2026, 9, 26));
		return payoutRepository.save(payout);
	}

	private PayoutStatus statusOf(Payout payout) {
		return payoutRepository.findById(payout.getId()).orElseThrow().getStatus();
	}

	@Test
	@DisplayName("캐시 서비스를 부르기 전에 지급 중을 커밋하므로, 호출하는 동안 걸린 보류는 409로 거절된다")
	void holdDuringTransferIsRejected() {
		Payout payout = savePayoutWithSale();
		List<PayoutStatus> statusDuringTransfer = new ArrayList<>();
		List<RuntimeException> holdErrors = new ArrayList<>();
		PayoutPayUseCase useCase = new PayoutPayUseCase(payoutPayStepUseCase, transfer -> {
			// 배치 트랜잭션 밖이라, 여기서 읽는 값은 다른 요청(관리자 보류 API)이 보는 커밋된 값이다
			statusDuringTransfer.add(statusOf(payout));
			try {
				payoutHoldUseCase.hold(payout.getId(), PayoutHoldReason.ACCOUNT_NOT_REGISTERED, null, 99, NOW);
			} catch (RuntimeException exception) {
				holdErrors.add(exception);
			}
		});

		useCase.run(PAY_DATE, NOW);

		assertThat(statusDuringTransfer).containsExactly(PayoutStatus.PAYING);
		assertThat(holdErrors).singleElement().isInstanceOf(PayoutHoldConflictException.class);
		assertThat(statusOf(payout)).isEqualTo(PayoutStatus.PAID);
		assertThat(payoutHoldRepository.count()).isZero();
	}

	@Test
	@DisplayName("두 배치가 같은 정산 예정을 동시에 읽어도, 늦게 지급 중으로 커밋한 쪽은 낙관적 락으로 실패한다")
	void secondRunFailsOnOptimisticLock() {
		Payout payout = savePayoutWithSale();
		TransactionTemplate slowRun = new TransactionTemplate(transactionManager);
		TransactionTemplate fastRun = new TransactionTemplate(transactionManager);
		fastRun.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

		assertThatThrownBy(() -> slowRun.executeWithoutResult(status -> {
			// 느린 배치가 정산 예정을 읽어 둔 사이에
			Payout stale = payoutRepository.findById(payout.getId()).orElseThrow();
			// 빠른 배치가 먼저 지급 중으로 커밋한다
			fastRun.executeWithoutResult(inner -> payoutPayStepUseCase.start(payout.getId(), PAY_DATE, NOW));
			stale.startPaying(NOW);
		})).isInstanceOf(ObjectOptimisticLockingFailureException.class);

		assertThat(statusOf(payout)).isEqualTo(PayoutStatus.PAYING);
	}

	@Test
	@DisplayName("다른 배치가 같은 지급 중 건을 먼저 지급 완료했으면 지급 완료를 다시 바꾸지 않는다")
	void completeIsNoOpWhenAlreadyPaid() {
		Payout payout = savePayoutWithSale();
		payoutPayStepUseCase.start(payout.getId(), PAY_DATE, NOW);
		payoutPayStepUseCase.complete(payout.getId(), NOW);

		boolean changed = payoutPayStepUseCase.complete(payout.getId(), NOW.plusHours(1));

		assertThat(changed).isFalse();
		assertThat(payoutRepository.findById(payout.getId()).orElseThrow().getPaidAt()).isEqualTo(NOW);
	}
}
