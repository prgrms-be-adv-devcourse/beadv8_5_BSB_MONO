package com.bukang.product.dto;

import java.time.LocalDateTime;

import com.bukang.product.domain.BaseRaceSyncEvent;
import com.bukang.product.domain.RaceDocumentSyncEvent;
import com.bukang.product.domain.SyncStatus;

/**
 * 검색 문서 동기화 행(문서·임베딩) 응답 (재처리 내부 API /internal/v1/product/race-sync-failures, BUK-162)
 */
public record RaceSyncEventDto(
	long id,
	long raceId,
	SyncStatus status,
	// 처리하지 못한 횟수. 재처리하면 0으로 돌아간다
	int retryCount,
	// 마지막으로 처리 실패가 된 시각 (실패 알림을 보낸 시각)
	LocalDateTime failedDate,
	LocalDateTime createDate,
	LocalDateTime completeDate,
	// 사람이 닫을 때 남긴 메모 (문서 동기화만. 임베딩 동기화와 닫지 않은 행은 null)
	String completeMemo
) {
	public static RaceSyncEventDto from(BaseRaceSyncEvent event) {
		String completeMemo = event instanceof RaceDocumentSyncEvent document ? document.getCompleteMemo() : null;
		return new RaceSyncEventDto(event.getId(), event.getRaceId(), event.getStatus(), event.getRetryCount(),
			event.getFailedDate(), event.getCreateDate(), event.getCompleteDate(), completeMemo);
	}
}
