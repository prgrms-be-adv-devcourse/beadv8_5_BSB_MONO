package com.bukang.payout.app;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import com.bukang.payout.app.PayoutPayStepUseCase.Start;
import com.bukang.payout.dto.PayoutPayResultDto;
import com.bukang.payout.out.PayoutTransferPort;
import com.bukang.payout.out.PayoutTransferRejectedException;
import com.bukang.payout.out.PayoutTransferUnknownException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 지급 배치: 지급 예정일이 된 정산 예정(CALCULATED) 정산 내역의 정산금을 캐시 서비스에 옮기고 지급 완료(PAID)로 바꾼다
 * (payout.md 2-6, PRO-252)
 * 보류(HELD)는 상태가 달라 고르지 않는다. 보류가 풀리면 PRO-26이 지급 예정일을 다시 정하고 CALCULATED로 돌린다
 *
 * 정산 내역 한 건씩 처리한다. 한 건이 실패해도 나머지는 지급한다
 * 1. 지급 중(PAYING)으로 바꿔 커밋한다 (짧은 트랜잭션)
 * 2. 트랜잭션 밖에서 캐시 서비스에 정산금 이동을 요청한다 (동기 HTTP)
 * 3. 결과에 따라 새 트랜잭션에서 상태를 바꾼다
 *    - 성공: 지급 완료(PAID)
 *    - 거절(4xx): 돈이 안 나간 것이 확실하므로 정산 예정(CALCULATED)으로 되돌린다. 다음 실행에서 다시 지급한다
 *    - 모름(타임아웃 · 5xx): 돈이 나갔을 수 있으므로 지급 중(PAYING)으로 둔다. 다음 실행에서 같은 정산 ID로 다시 요청하고,
 *      캐시 서비스는 이미 옮겼으면 돈을 다시 옮기지 않고 성공으로 답한다
 * 대금 · 수수료가 둘 다 0원이면 캐시 서비스를 부르지 않고 지급 완료한다 (0원 지급 완료, payout.md Q20)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutPayUseCase {
	private static final String REJECTED_REASON = "캐시 서비스 거절";

	private final PayoutPayStepUseCase payoutPayStepUseCase;
	private final PayoutTransferPort payoutTransferPort;

	public PayoutPayResultDto run(LocalDate today, LocalDateTime now) {
		int paid = 0;
		int failed = 0;
		int unknown = 0;
		for (long payoutId : payoutPayStepUseCase.findTargetIds(today)) {
			switch (payOne(payoutId, today, now)) {
				case PAID -> paid++;
				case FAILED -> failed++;
				case UNKNOWN -> unknown++;
				case SKIPPED -> {
				}
			}
		}
		if (failed > 0 || unknown > 0) {
			log.error("지급 배치: 지급 완료 {}건, 실패(정산 예정 유지) {}건, 결과 모름(지급 중 유지) {}건", paid, failed, unknown);
		} else {
			log.info("지급 배치: 지급 완료 {}건", paid);
		}
		return new PayoutPayResultDto(today, paid, failed, unknown);
	}

	private Result payOne(long payoutId, LocalDate today, LocalDateTime now) {
		Start start;
		try {
			start = payoutPayStepUseCase.start(payoutId, today, now);
		} catch (OptimisticLockingFailureException exception) {
			// 같은 정산 내역을 다른 배치 · 보류 API가 먼저 바꿨다. 그쪽 결과를 따른다
			log.info("지급 시작 중 다른 변경과 부딪혀 건너뜁니다. payoutId={}", payoutId);
			return Result.SKIPPED;
		} catch (RuntimeException exception) {
			// 지급 중으로 바꾸기 전에 실패했으므로 돈은 나가지 않았다
			log.error("지급을 시작하지 못했습니다. payoutId={}", payoutId, exception);
			return Result.FAILED;
		}

		return switch (start.outcome()) {
			case SKIPPED -> Result.SKIPPED;
			case PAID_WITHOUT_TRANSFER -> Result.PAID;
			case TRANSFER -> transfer(start, now);
		};
	}

	private Result transfer(Start start, LocalDateTime now) {
		long payoutId = start.transfer().payoutId();
		try {
			payoutTransferPort.transfer(start.transfer());
		} catch (PayoutTransferRejectedException exception) {
			log.error("캐시 서비스가 정산금 이동을 거절해 정산 예정으로 되돌립니다. payoutId={}, cause={}",
				payoutId, exception.getMessage());
			return revert(payoutId, now);
		} catch (PayoutTransferUnknownException exception) {
			log.error("정산금 이동 결과를 몰라 지급 중으로 두고 다음 실행에서 다시 요청합니다. payoutId={}, cause={}",
				payoutId, exception.getMessage());
			return Result.UNKNOWN;
		} catch (RuntimeException exception) {
			// 어댑터 밖의 예상 못 한 오류도 돈이 나갔는지 모르는 것으로 본다
			log.error("정산금 이동 중 예상 못 한 오류가 나서 지급 중으로 둡니다. payoutId={}", payoutId, exception);
			return Result.UNKNOWN;
		}

		try {
			payoutPayStepUseCase.complete(payoutId, now);
			return Result.PAID;
		} catch (RuntimeException exception) {
			// 돈은 옮겨졌는데 지급 완료를 커밋하지 못했다. 지급 중으로 남아 다음 실행이 다시 요청하면 캐시 서비스 멱등으로 완료된다
			log.error("정산금은 옮겼지만 지급 완료로 바꾸지 못해 지급 중으로 둡니다. payoutId={}", payoutId, exception);
			return Result.UNKNOWN;
		}
	}

	private Result revert(long payoutId, LocalDateTime now) {
		try {
			payoutPayStepUseCase.revert(payoutId, now, REJECTED_REASON);
			return Result.FAILED;
		} catch (RuntimeException exception) {
			// 되돌리지 못하면 지급 중으로 남는다. 다음 실행이 다시 요청하고, 또 거절되면 그때 되돌린다
			log.error("거절된 정산 내역을 정산 예정으로 되돌리지 못해 지급 중으로 둡니다. payoutId={}", payoutId, exception);
			return Result.UNKNOWN;
		}
	}

	private enum Result {
		PAID,
		FAILED,
		UNKNOWN,
		SKIPPED
	}
}
