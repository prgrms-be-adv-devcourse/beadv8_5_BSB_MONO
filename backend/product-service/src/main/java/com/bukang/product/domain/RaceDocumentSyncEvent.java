package com.bukang.product.domain;

import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import com.bukang.product.domain.exception.RaceSyncEventStatusException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 칸과 상태 변경은 임베딩 동기화 이벤트와 같아 BaseRaceSyncEvent에 있다.
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(
	name = "PRODUCT_RACE_DOCUMENT_SYNC_EVENT",
	// 미처리(PENDING) 행을 만든 순서대로 빠르게 찾는다 (스케줄러가 쓴다)
	indexes = @Index(name = "idx_race_document_sync_status_create", columnList = "status, create_date")
)
public class RaceDocumentSyncEvent extends BaseRaceSyncEvent {
	// 사람이 처리 실패 행을 다시 처리하지 않고 닫을 때 남긴 메모 (예: "Kibana에서 문서를 직접 고침", BUK-162)
	// 임베딩 동기화에는 없다. 벡터는 사람이 만들 수 없어 원인을 고친 뒤 재처리하는 길만 둔다
	@Column(name = "complete_memo", length = 500)
	private String completeMemo;

	private RaceDocumentSyncEvent(long raceId) {
		super(raceId);
	}

	public static RaceDocumentSyncEvent pending(long raceId) {
		return new RaceDocumentSyncEvent(raceId);
	}

	// 사람이 검색 문서를 직접 고친 뒤 처리 실패 행을 메모와 함께 처리 완료로 닫는다 (다시 처리하지 않는다)
	// 처리 실패 상태에서만 닫는다. 미처리면 스케줄러가 곧 처리하고, 처리 완료면 이미 끝났다
	public void completeManually(LocalDateTime now, String memo) {
		if (!isFailed()) {
			throw new RaceSyncEventStatusException(getId(), getStatus(), "처리 완료로 닫을");
		}
		if (memo == null || memo.isBlank()) {
			throw new IllegalArgumentException("처리 완료 메모를 적어야 합니다.");
		}
		markComplete(now);
		this.completeMemo = memo.strip();
	}
}
