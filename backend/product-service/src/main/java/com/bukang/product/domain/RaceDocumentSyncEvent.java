package com.bukang.product.domain;

import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대회 1개의 검색 문서(race_courses)를 DB에 맞출 일 1건 (대회 검색 문서 동기화 이벤트).
 * 대회를 저장하는 트랜잭션 안에서 함께 저장하므로, 저장이 커밋되면 행도 반드시 남고 롤백되면 행도 없다.
 * 처리는 같은 서비스의 스케줄러(RaceDocumentSyncScheduler)가 한다. 성공하면 COMPLETE, 실패하면 PENDING으로 두고 재시도 시각 뒤에 다시 처리한다.
 * Kafka 아웃박스와 달리 보내는 쪽과 처리하는 쪽이 같아서, 처리했는지까지 이 행 하나로 기록한다 (인박스를 따로 두지 않는다).
 * 처리는 DB의 최신 대회·종목을 읽어 문서를 맞추므로, 행에는 대회 ID만 둔다 (Kafka 아웃박스와 달리 payload가 없다).
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "PRODUCT_RACE_DOCUMENT_SYNC_EVENT",
	// 미처리(PENDING) 행을 만든 순서대로 빠르게 찾는다 (스케줄러가 쓴다)
	indexes = @Index(name = "idx_race_document_sync_status_create", columnList = "status, create_date")
)
public class RaceDocumentSyncEvent extends BaseIdAndTime {
	// 검색 문서를 맞출 대회 ID
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

	private RaceDocumentSyncEvent(long raceId) {
		this.raceId = raceId;
		this.status = SyncStatus.PENDING;
	}

	public static RaceDocumentSyncEvent pending(long raceId) {
		return new RaceDocumentSyncEvent(raceId);
	}

	public void markComplete(LocalDateTime now) {
		this.status = SyncStatus.COMPLETE;
		this.completeDate = now;
	}

	// 처리하지 못한 횟수를 올리고, maxRetry번째 실패면 처리 실패로 바꿔 더는 처리하지 않는다
	// 아직 재시도할 수 있으면 nextRetryDate 전에는 다시 처리하지 않는다
	public void markFailed(int maxRetry, LocalDateTime nextRetryDate) {
		this.retryCount++;
		if (retryCount >= maxRetry) {
			this.status = SyncStatus.FAILED;
			return;
		}
		this.nextRetryDate = nextRetryDate;
	}

	// 지금 처리해도 되는지 (실패한 적이 없거나, 재시도 시각이 지났다)
	public boolean isProcessableAt(LocalDateTime now) {
		return nextRetryDate == null || !nextRetryDate.isAfter(now);
	}

	public boolean isFailed() {
		return status == SyncStatus.FAILED;
	}
}
