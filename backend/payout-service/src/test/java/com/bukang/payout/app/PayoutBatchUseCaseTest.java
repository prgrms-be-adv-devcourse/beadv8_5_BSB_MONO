package com.bukang.payout.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.payout.app.PayoutClaimUseCase.ClaimRunResult;
import com.bukang.payout.domain.PayoutBatchRun;
import com.bukang.payout.domain.PayoutBatchRunStatus;
import com.bukang.payout.domain.PayoutBatchTrigger;
import com.bukang.payout.domain.PayoutBatchType;
import com.bukang.payout.domain.exception.PayoutBatchRunningException;
import com.bukang.payout.dto.PayoutBatchRunDto;
import com.bukang.payout.dto.PayoutCreateResultDto;
import com.bukang.payout.dto.PayoutPayResultDto;
import com.bukang.payout.global.config.PayoutSchedulerLockConfig;
import com.bukang.payout.out.PayoutBatchRunRepository;

/**
 * 정산 배치 잠금 · 실행 기록 (BUK-167). 잠금 표와 실행 기록 표는 진짜(H2)로, 배치 UseCase는 가짜로 둔다
 */
@DataJpaTest
@ActiveProfiles("test")
class PayoutBatchUseCaseTest {
	private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 4, 0);
	private static final long ADMIN_ID = 99;

	@Autowired
	private PayoutBatchRunRepository payoutBatchRunRepository;
	@Autowired
	private DataSource dataSource;
	@Autowired
	private PlatformTransactionManager transactionManager;

	private final PayoutCreateUseCase createUseCase = mock(PayoutCreateUseCase.class);
	private final PayoutReconcileUseCase reconcileUseCase = mock(PayoutReconcileUseCase.class);
	private final PayoutPayUseCase payUseCase = mock(PayoutPayUseCase.class);
	private final PayoutClaimUseCase claimUseCase = mock(PayoutClaimUseCase.class);
	private PayoutBatchUseCase useCase;

	@BeforeEach
	void setUp() {
		PayoutSchedulerLockConfig lockConfig = new PayoutSchedulerLockConfig();
		useCase = new PayoutBatchUseCase(
			lockConfig.payoutLockingTaskExecutor(lockConfig.payoutLockProvider(dataSource)),
			createUseCase, reconcileUseCase, payUseCase, claimUseCase, payoutBatchRunRepository);
		given(createUseCase.run(any(), any())).willReturn(new PayoutCreateResultDto(TODAY, TODAY, 3, 1, 0));
		given(payUseCase.run(any(), any())).willReturn(new PayoutPayResultDto(TODAY, 5, 0, 0, 0));
		given(claimUseCase.run(any(), any())).willReturn(new ClaimRunResult(1, 1, 0));
	}

	// 실행 기록(REQUIRES_NEW)과 잠금 행은 테스트 롤백으로 지워지지 않는다. 잠금은 끝난 뒤에도 30초 쥐고 있으므로
	// 다음 테스트가 막히지 않게 새 트랜잭션에서 둘 다 지운다
	@AfterEach
	void deleteRunsAndLocks() {
		TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
		requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		requiresNew.executeWithoutResult(status -> {
			payoutBatchRunRepository.deleteAllInBatch();
			new JdbcTemplate(dataSource).update("delete from payout_shedlock");
		});
	}

	@Test
	@DisplayName("생성 배치는 정산 생성 뒤 대사를 돌리고, 만든 건수 · 실패 건수로 완료 한 줄을 남긴다")
	void createRecordsCompletedRun() {
		List<PayoutBatchRunDto> runs = useCase.runCreate(PayoutBatchTrigger.관리자, ADMIN_ID, TODAY, NOW);

		InOrder order = inOrder(createUseCase, reconcileUseCase);
		order.verify(createUseCase).run(TODAY, NOW);
		order.verify(reconcileUseCase).run(TODAY, NOW);
		assertThat(runs).extracting(PayoutBatchRunDto::batchType, PayoutBatchRunDto::triggerType,
				PayoutBatchRunDto::startedBy, PayoutBatchRunDto::status, PayoutBatchRunDto::doneCount,
				PayoutBatchRunDto::problemCount)
			.containsExactly(tuple(PayoutBatchType.생성, PayoutBatchTrigger.관리자, ADMIN_ID,
				PayoutBatchRunStatus.완료, 3, 0));
		assertThat(payoutBatchRunRepository.findAll()).singleElement()
			.extracting(PayoutBatchRun::getStatus).isEqualTo(PayoutBatchRunStatus.완료);
	}

	@Test
	@DisplayName("AC-67-10 대사가 실패해도 밖으로 내지 않고, 생성 기록을 일부 실패로 남긴다 (정산 생성은 이미 끝났다)")
	void reconcileFailureIsPartialFailure() {
		doThrow(new IllegalStateException("DB 연결 끊김")).when(reconcileUseCase).run(any(), any());

		List<PayoutBatchRunDto> runs = useCase.runCreate(PayoutBatchTrigger.스케줄, null, TODAY, NOW);

		assertThat(runs).singleElement().satisfies(run -> {
			assertThat(run.status()).isEqualTo(PayoutBatchRunStatus.일부_실패);
			assertThat(run.problemCount()).isEqualTo(1);
			assertThat(run.summary()).contains("대사를 마치지 못함");
		});
	}

	@Test
	@DisplayName("지급 배치는 지급 · 청구 두 줄을 남기고, 결과 모름이 1건이라도 있으면 지급 줄은 일부 실패다")
	void payRecordsPayAndClaimRuns() {
		given(payUseCase.run(any(), any())).willReturn(new PayoutPayResultDto(TODAY, 5, 0, 1, 0));

		List<PayoutBatchRunDto> runs = useCase.runPay(PayoutBatchTrigger.스케줄, null, TODAY, NOW);

		assertThat(runs).extracting(PayoutBatchRunDto::batchType, PayoutBatchRunDto::status,
				PayoutBatchRunDto::doneCount, PayoutBatchRunDto::problemCount)
			.containsExactly(
				tuple(PayoutBatchType.지급, PayoutBatchRunStatus.일부_실패, 5, 1),
				tuple(PayoutBatchType.청구, PayoutBatchRunStatus.완료, 1, 0));
	}

	@Test
	@DisplayName("지급이 예외로 멈추면 지급 줄은 실패로 남고, 청구는 그대로 돈다")
	void payExceptionIsFailedAndClaimStillRuns() {
		given(payUseCase.run(any(), any())).willThrow(new IllegalStateException("예상 못 한 오류"));

		List<PayoutBatchRunDto> runs = useCase.runPay(PayoutBatchTrigger.스케줄, null, TODAY, NOW);

		assertThat(runs).extracting(PayoutBatchRunDto::batchType, PayoutBatchRunDto::status)
			.containsExactly(
				tuple(PayoutBatchType.지급, PayoutBatchRunStatus.실패),
				tuple(PayoutBatchType.청구, PayoutBatchRunStatus.완료));
		assertThat(runs.getFirst().summary()).startsWith("IllegalStateException");
	}

	@Test
	@DisplayName("PR #431 리뷰: 1시간보다 오래 진행 중으로 남은 같은 배치 기록은 다음 실행이 실패로 닫는다. 다른 배치 기록은 그대로다")
	void nextRunClosesStaleRunningRecord() {
		PayoutBatchRun stalePay = payoutBatchRunRepository.save(
			new PayoutBatchRun(PayoutBatchType.지급, PayoutBatchTrigger.스케줄, TODAY.minusDays(1), null,
				NOW.minusDays(1)));
		PayoutBatchRun staleCreate = payoutBatchRunRepository.save(
			new PayoutBatchRun(PayoutBatchType.생성, PayoutBatchTrigger.스케줄, TODAY.minusDays(1), null,
				NOW.minusDays(1)));

		useCase.runPay(PayoutBatchTrigger.스케줄, null, TODAY, NOW);

		assertThat(payoutBatchRunRepository.findById(stalePay.getId()).orElseThrow())
			.extracting(PayoutBatchRun::getStatus, PayoutBatchRun::getFinishedAt)
			.containsExactly(PayoutBatchRunStatus.실패, NOW);
		assertThat(payoutBatchRunRepository.findById(staleCreate.getId()).orElseThrow().getStatus())
			.isEqualTo(PayoutBatchRunStatus.진행_중);
	}

	@Test
	@DisplayName("BUK-167 같은 배치의 잠금을 다른 곳이 쥐고 있으면 돌리지 않고 409 예외를 던진다. 기록도 남지 않는다")
	void throwsWhenLockIsHeld() {
		new JdbcTemplate(dataSource).update("""
			insert into payout_shedlock (name, lock_until, locked_at, locked_by)
			values (?, timestampadd(hour, 1, current_timestamp), current_timestamp, '다른 서버')""",
			PayoutBatchUseCase.PAY_LOCK);

		assertThatThrownBy(() -> useCase.runPay(PayoutBatchTrigger.관리자, ADMIN_ID, TODAY, NOW))
			.isInstanceOf(PayoutBatchRunningException.class);
		verify(payUseCase, never()).run(any(), any());
		assertThat(payoutBatchRunRepository.findAll()).isEmpty();
	}
}
