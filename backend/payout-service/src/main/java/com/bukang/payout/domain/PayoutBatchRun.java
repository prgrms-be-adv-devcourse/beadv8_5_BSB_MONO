package com.bukang.payout.domain;

import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 정산 배치 실행 한 번의 기록 (BUK-167). 시작할 때 진행 중으로 한 줄 넣고, 끝나면 결과로 바꾼다
 * 관리자가 "이번 실행에서 몇 건을 처리하고 몇 건에 문제가 있었나"를 한 줄로 본다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(name = "PAYOUT_BATCH_RUN")
public class PayoutBatchRun extends BaseIdAndTime {
	private static final int SUMMARY_MAX_LENGTH = 255;

	@Enumerated(STRING)
	@Column(nullable = false, length = 10)
	private PayoutBatchType batchType;

	@Enumerated(STRING)
	@Column(nullable = false, length = 10)
	private PayoutBatchTrigger triggerType;

	// 배치의 실행 기준일(today). 관리자가 다른 날을 넣어 돌리면 그 날이다
	@Column(nullable = false)
	private LocalDate runDate;

	// 관리자가 돌렸으면 관리자 회원 ID, 스케줄이면 NULL
	private Long startedBy;

	@Column(nullable = false)
	private LocalDateTime startedAt;

	private LocalDateTime finishedAt;

	@Enumerated(STRING)
	@Column(nullable = false, length = 10)
	private PayoutBatchRunStatus status;

	// 처리한 건수: 생성은 만든 정산 내역, 지급은 지급 완료, 청구는 만든 청구
	@Column(nullable = false)
	private int doneCount;

	// 문제 건수: 생성은 실패, 지급은 실패 · 결과 모름 · 불일치. 청구는 0
	@Column(nullable = false)
	private int problemCount;

	// 사람이 읽는 결과 요약이나 멈춘 예외 이름
	@Column(length = SUMMARY_MAX_LENGTH)
	private String summary;

	public PayoutBatchRun(PayoutBatchType batchType, PayoutBatchTrigger triggerType, LocalDate runDate,
		Long startedBy, LocalDateTime startedAt) {
		this.batchType = batchType;
		this.triggerType = triggerType;
		this.runDate = runDate;
		this.startedBy = startedBy;
		this.startedAt = startedAt;
		this.status = PayoutBatchRunStatus.진행_중;
	}

	// 끝까지 돌았다. 문제 건이 하나라도 있으면 일부 실패다
	public void finish(int doneCount, int problemCount, String summary, LocalDateTime finishedAt) {
		this.doneCount = doneCount;
		this.problemCount = problemCount;
		this.summary = truncate(summary);
		this.status = problemCount > 0 ? PayoutBatchRunStatus.일부_실패 : PayoutBatchRunStatus.완료;
		this.finishedAt = finishedAt;
	}

	// 예외로 중간에 멈췄다
	public void fail(String reason, LocalDateTime finishedAt) {
		this.summary = truncate(reason);
		this.status = PayoutBatchRunStatus.실패;
		this.finishedAt = finishedAt;
	}

	private static String truncate(String value) {
		if (value == null || value.length() <= SUMMARY_MAX_LENGTH) {
			return value;
		}
		return value.substring(0, SUMMARY_MAX_LENGTH);
	}
}
