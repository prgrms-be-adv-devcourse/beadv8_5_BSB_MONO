package com.bukang.payout.app;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockingTaskExecutor.TaskResult;

import com.bukang.payout.app.PayoutClaimUseCase.ClaimRunResult;
import com.bukang.payout.domain.PayoutBatchRun;
import com.bukang.payout.domain.PayoutBatchRunStatus;
import com.bukang.payout.domain.PayoutBatchTrigger;
import com.bukang.payout.domain.PayoutBatchType;
import com.bukang.payout.domain.exception.PayoutBatchRunningException;
import com.bukang.payout.dto.PayoutBatchRunDto;
import com.bukang.payout.dto.PayoutCreateResultDto;
import com.bukang.payout.dto.PayoutPayResultDto;
import com.bukang.payout.out.PayoutBatchRunRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 정산 배치를 잠금 안에서 돌리고 실행 기록을 남긴다 (BUK-167). 스케줄러와 관리자 API가 같은 길로 부른다
 * - 잠금: 생성 · 지급(청구 포함)마다 하나. 이미 돌고 있으면 PayoutBatchRunningException(409)을 던진다
 * - 기록: 배치 종류마다 시작할 때 진행 중 한 줄을 커밋하고, 끝나면 완료 · 일부 실패 · 실패로 바꾼다
 * 트랜잭션은 열지 않는다. 각 배치 UseCase가 자기 트랜잭션을 열고, 실행 기록은 따로 커밋된다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutBatchUseCase {
	static final String CREATE_LOCK = "payout-create";
	static final String PAY_LOCK = "payout-pay";
	// 서버가 죽어도 이 시간이 지나면 잠금이 풀린다. 배치 한 번은 이보다 짧다
	private static final Duration LOCK_AT_MOST = Duration.ofHours(1);
	// 서버마다 시계가 조금 달라도 같은 스케줄이 두 번 돌지 않게 끝난 뒤 잠시 잠금을 쥔다
	private static final Duration LOCK_AT_LEAST = Duration.ofSeconds(30);
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
	private static final String STALE_RUN_REASON = "끝나지 않고 멈춤 (서버가 배치 도중 꺼진 것으로 보임)";

	private final LockingTaskExecutor payoutLockingTaskExecutor;
	private final PayoutCreateUseCase payoutCreateUseCase;
	private final PayoutReconcileUseCase payoutReconcileUseCase;
	private final PayoutPayUseCase payoutPayUseCase;
	private final PayoutClaimUseCase payoutClaimUseCase;
	private final PayoutBatchRunRepository payoutBatchRunRepository;

	// 정산 생성 뒤 같은 기준일 · 시작 시각으로 대사를 돌린다. 대사가 실패해도 생성 결과는 이미 커밋됐다 (AC-67-10)
	public List<PayoutBatchRunDto> runCreate(PayoutBatchTrigger trigger, Long startedBy, LocalDate today,
		LocalDateTime now) {
		return withLock(CREATE_LOCK, "정산 생성", () -> {
			closeStaleRuns(List.of(PayoutBatchType.생성), now);
			return List.of(record(PayoutBatchType.생성, trigger, startedBy, today, now, () -> create(today, now)));
		});
	}

	// 지급 뒤 회수 청구 · 알림. 지급은 실패해도 예외를 던지지 않아 청구가 막히지 않는다 (선 환불 후 회수)
	public List<PayoutBatchRunDto> runPay(PayoutBatchTrigger trigger, Long startedBy, LocalDate today,
		LocalDateTime now) {
		return withLock(PAY_LOCK, "정산 지급", () -> {
			closeStaleRuns(List.of(PayoutBatchType.지급, PayoutBatchType.청구), now);
			return List.of(
				record(PayoutBatchType.지급, trigger, startedBy, today, now, () -> pay(today, now)),
				record(PayoutBatchType.청구, trigger, startedBy, today, now, () -> claim(today, now)));
		});
	}

	private Outcome create(LocalDate today, LocalDateTime now) {
		PayoutCreateResultDto result = payoutCreateUseCase.run(today, now);
		String summary = "만듦 %d · 건너뜀 %d · 실패 %d".formatted(result.created(), result.skipped(), result.failed());
		try {
			payoutReconcileUseCase.run(today, now);
		} catch (RuntimeException exception) {
			log.error("정산 대사를 마치지 못했습니다. today={}", today, exception);
			return new Outcome(result.created(), result.failed() + 1, summary + " · 대사를 마치지 못함");
		}
		return new Outcome(result.created(), result.failed(), summary);
	}

	private Outcome pay(LocalDate today, LocalDateTime now) {
		PayoutPayResultDto result = payoutPayUseCase.run(today, now);
		return new Outcome(result.paid(), result.failed() + result.unknown() + result.mismatch(),
			"지급 완료 %d · 실패 %d · 결과 모름 %d · 불일치 %d".formatted(result.paid(), result.failed(), result.unknown(),
				result.mismatch()));
	}

	private Outcome claim(LocalDate today, LocalDateTime now) {
		ClaimRunResult result = payoutClaimUseCase.run(today, now);
		return new Outcome(result.created(), 0, "청구 %d · 판매자 알림 %d · 관리자 알림 %d".formatted(result.created(),
			result.sellerNotices(), result.adminNotices()));
	}

	// 서버가 배치 도중 꺼지면 끝 줄을 못 써서 진행 중으로 남는다 (PR #431 리뷰, 11/12 결정)
	// 잠금 최대 시간(1시간)보다 오래된 진행 중은 이미 멈춘 실행이므로, 같은 배치를 다시 시작할 때 실패로 닫는다
	private void closeStaleRuns(List<PayoutBatchType> types, LocalDateTime now) {
		for (PayoutBatchRun stale : payoutBatchRunRepository.findByBatchTypeInAndStatusAndStartedAtBefore(types,
			PayoutBatchRunStatus.진행_중, now.minus(LOCK_AT_MOST))) {
			stale.fail(STALE_RUN_REASON, now);
			payoutBatchRunRepository.save(stale);
			log.warn("진행 중으로 남은 정산 배치 기록을 실패로 닫습니다. runId={}, type={}, startedAt={}",
				stale.getId(), stale.getBatchType(), stale.getStartedAt());
		}
	}

	// 진행 중 한 줄을 먼저 커밋하고, 일을 마치면 결과로 바꾼다. 예외로 멈추면 실패로 남기고 다음 배치는 계속한다
	private PayoutBatchRunDto record(PayoutBatchType type, PayoutBatchTrigger trigger, Long startedBy,
		LocalDate today, LocalDateTime now, Supplier<Outcome> work) {
		PayoutBatchRun run = payoutBatchRunRepository.save(new PayoutBatchRun(type, trigger, today, startedBy, now));
		try {
			Outcome outcome = work.get();
			run.finish(outcome.done(), outcome.problem(), outcome.summary(), LocalDateTime.now(ZONE));
		} catch (RuntimeException exception) {
			log.error("정산 {} 배치가 멈췄습니다. today={}", type, today, exception);
			run.fail(exception.getClass().getSimpleName() + ": " + exception.getMessage(), LocalDateTime.now(ZONE));
		}
		return PayoutBatchRunDto.from(payoutBatchRunRepository.save(run));
	}

	private List<PayoutBatchRunDto> withLock(String lockName, String label, Supplier<List<PayoutBatchRunDto>> task) {
		TaskResult<List<PayoutBatchRunDto>> result;
		try {
			result = payoutLockingTaskExecutor.executeWithLock(task::get,
				new LockConfiguration(Instant.now(), lockName, LOCK_AT_MOST, LOCK_AT_LEAST));
		} catch (RuntimeException exception) {
			throw exception;
		} catch (Throwable throwable) {
			throw new IllegalStateException(label + " 배치를 잠금 안에서 돌리지 못했습니다.", throwable);
		}
		if (!result.wasExecuted()) {
			throw new PayoutBatchRunningException(label);
		}
		return result.getResult();
	}

	private record Outcome(int done, int problem, String summary) {
	}
}
