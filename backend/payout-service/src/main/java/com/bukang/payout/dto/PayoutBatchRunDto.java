package com.bukang.payout.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.bukang.payout.domain.PayoutBatchRun;
import com.bukang.payout.domain.PayoutBatchRunStatus;
import com.bukang.payout.domain.PayoutBatchTrigger;
import com.bukang.payout.domain.PayoutBatchType;

/**
 * 정산 배치 실행 기록 한 줄 (BUK-167). 관리자 배치 실행 · 실행 기록 조회 응답에 쓴다
 *
 * @param doneCount    처리한 건수 (생성: 만든 정산 내역, 지급: 지급 완료, 청구: 만든 청구)
 * @param problemCount 문제 건수 (생성: 실패, 지급: 실패 · 결과 모름 · 불일치)
 */
public record PayoutBatchRunDto(
	long id,
	PayoutBatchType batchType,
	PayoutBatchTrigger triggerType,
	LocalDate runDate,
	Long startedBy,
	LocalDateTime startedAt,
	LocalDateTime finishedAt,
	PayoutBatchRunStatus status,
	int doneCount,
	int problemCount,
	String summary
) {
	public static PayoutBatchRunDto from(PayoutBatchRun run) {
		return new PayoutBatchRunDto(run.getId(), run.getBatchType(), run.getTriggerType(), run.getRunDate(),
			run.getStartedBy(), run.getStartedAt(), run.getFinishedAt(), run.getStatus(), run.getDoneCount(),
			run.getProblemCount(), run.getSummary());
	}
}
