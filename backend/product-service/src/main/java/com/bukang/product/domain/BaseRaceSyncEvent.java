package com.bukang.product.domain;

import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;
import com.bukang.product.domain.exception.RaceSyncEventStatusException;

import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대회 1개를 검색 문서에 맞출 일 1건의 공통 칸과 상태 변경 (문서 동기화·임베딩 동기화 이벤트의 부모, BUK-162).
 * 두 이벤트는 표만 다르고 칸과 처리 방식이 같다. 표 이름과 인덱스는 상속한 엔티티가 @Table로 정한다.
 *
 * 대회 저장 → [pending] 미처리(PENDING)
 *     │ 스케줄러가 처리
 *     ├ 성공 → [markComplete] 처리 완료(COMPLETE). 실패 알림이 간 적 있는 행(hasFailed)이면 완료 알림
 *     └ 실패 → [markFailed] 재시도 시각 뒤 다시 처리. maxRetry번 실패하면 처리 실패(FAILED) + 실패 알림
 *                 │ 사람이 원인을 고침
 *                 ├ [retry] 재처리 API 또는 DB에서 직접 PENDING으로 되돌림 → 다시 스케줄러가 처리
 *                 └ [completeManually] 문서 동기화만: 사람이 문서를 직접 고치고 메모와 함께 닫음 (RaceDocumentSyncEvent)
 */
@MappedSuperclass
@NoArgsConstructor(access = PROTECTED)
@Getter
public abstract class BaseRaceSyncEvent extends BaseIdAndTime {
	// 검색 문서(또는 그 벡터 칸)를 맞출 대회 ID
	@Column(name = "race_id", nullable = false)
	private long raceId;

	@Enumerated(STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
	private SyncStatus status;

	// 처리하지 못한 횟수
	@Column(name = "retry_count", nullable = false)
	private int retryCount;

	// 처리하지 못했을 때 다시 처리해도 되는 가장 이른 시각 (재시도 간격을 점점 늘린다). 한 번도 실패하지 않았으면 null
	@Column(name = "next_retry_date")
	private LocalDateTime nextRetryDate;

	// 처리를 마친 시각
	@Column(name = "complete_date")
	private LocalDateTime completeDate;

	// 마지막으로 처리 실패(FAILED)가 된 시각. 이때 실패 알림을 보낸다. 한 번도 FAILED가 된 적이 없으면 null
	// 처리 완료 때 이 값이 있으면 완료 알림을 보낸다. 재처리 API로 되돌렸든 DB에서 직접 되돌렸든 같다
	@Column(name = "failed_date")
	private LocalDateTime failedDate;

	protected BaseRaceSyncEvent(long raceId) {
		this.raceId = raceId;
		this.status = SyncStatus.PENDING;
	}

	public void markComplete(LocalDateTime now) {
		this.status = SyncStatus.COMPLETE;
		this.completeDate = now;
	}

	// 처리하지 못한 횟수를 올리고, maxRetry번째 실패면 처리 실패로 바꿔 더는 처리하지 않는다
	// 아직 재시도할 수 있으면 nextRetryDate 전에는 다시 처리하지 않는다
	public void markFailed(int maxRetry, LocalDateTime nextRetryDate, LocalDateTime now) {
		this.retryCount++;
		if (retryCount >= maxRetry) {
			this.status = SyncStatus.FAILED;
			this.failedDate = now;
			return;
		}
		this.nextRetryDate = nextRetryDate;
	}

	// 사람이 원인을 고친 뒤 처리 실패를 미처리로 되돌린다 (재처리 API). 스케줄러가 처음 처리하듯 다시 처리한다
	// DB에서 직접 되돌릴 때도 같은 세 칸(status, retry_count, next_retry_date)을 바꾼다
	public void retry() {
		if (status != SyncStatus.FAILED) {
			throw new RaceSyncEventStatusException(getId(), status, "재처리할");
		}
		this.status = SyncStatus.PENDING;
		this.retryCount = 0;
		this.nextRetryDate = null;
	}

	// 지금 처리해도 되는지 (실패한 적이 없거나, 재시도 시각이 지났다)
	public boolean isProcessableAt(LocalDateTime now) {
		return nextRetryDate == null || !nextRetryDate.isAfter(now);
	}

	public boolean isFailed() {
		return status == SyncStatus.FAILED;
	}

	// 처리 실패가 된 적이 있는지 (실패 알림이 간 행인지)
	public boolean hasFailed() {
		return failedDate != null;
	}
}
