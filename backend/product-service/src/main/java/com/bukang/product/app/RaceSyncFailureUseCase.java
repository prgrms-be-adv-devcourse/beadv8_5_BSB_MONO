package com.bukang.product.app;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import com.bukang.common.global.notify.SlackNotifier;
import com.bukang.product.domain.BaseRaceSyncEvent;
import com.bukang.product.domain.RaceDocumentSyncEvent;
import com.bukang.product.domain.RaceSyncType;
import com.bukang.product.domain.SyncStatus;
import com.bukang.product.domain.exception.RaceSyncEventNotFoundException;
import com.bukang.product.out.RaceDocumentSyncEventRepository;
import com.bukang.product.out.RaceEmbeddingSyncEventRepository;

import lombok.RequiredArgsConstructor;

/**
 * 처리 실패(FAILED)한 검색 문서 동기화 행(문서·임베딩)을 보고, 사람이 원인을 고친 뒤 재처리한다 (BUK-162).
 * 재처리는 행을 미처리(PENDING)로 되돌리기만 한다. 실제 처리는 그 종류의 동기화 스케줄러가 1초 안에 한다.
 * 처리되면 스케줄러가 Slack으로 완료를 알리고, 또 실패하면 다시 실패를 알린다.
 * 문서 동기화는 사람이 검색 문서를 직접 고친 뒤 다시 처리하지 않고 메모와 함께 닫을 수도 있다 (completeDocumentManually).
 * 트랜잭션은 ProductFacade가 연다.
 */
@Service
@RequiredArgsConstructor
public class RaceSyncFailureUseCase {
	private final RaceDocumentSyncEventRepository raceDocumentSyncEventRepository;
	private final RaceEmbeddingSyncEventRepository raceEmbeddingSyncEventRepository;
	private final SlackNotifier slackNotifier;

	// 처리 실패 행을 최근 것부터 limit건
	public List<? extends BaseRaceSyncEvent> findFailed(RaceSyncType type, int limit) {
		return switch (type) {
			case DOCUMENT -> raceDocumentSyncEventRepository.findByStatusOrderByIdDesc(SyncStatus.FAILED,
				Limit.of(limit));
			case EMBEDDING -> raceEmbeddingSyncEventRepository.findByStatusOrderByIdDesc(SyncStatus.FAILED,
				Limit.of(limit));
		};
	}

	// 처리 실패 → 미처리. 없는 행은 404, 처리 실패가 아니면 409
	public BaseRaceSyncEvent retry(RaceSyncType type, long id) {
		BaseRaceSyncEvent event = switch (type) {
			case DOCUMENT -> raceDocumentSyncEventRepository.findById(id)
				.orElseThrow(() -> new RaceSyncEventNotFoundException(id));
			case EMBEDDING -> raceEmbeddingSyncEventRepository.findById(id)
				.orElseThrow(() -> new RaceSyncEventNotFoundException(id));
		};
		event.retry();
		return event;
	}

	// 문서 동기화만: 사람이 검색 문서를 직접 고친 뒤 처리 실패 행을 메모와 함께 닫고, 커밋 뒤 Slack으로 알린다
	// 다시 처리하지 않는다. 임베딩 동기화에는 두지 않는다 (벡터는 사람이 만들 수 없다)
	public RaceDocumentSyncEvent completeDocumentManually(long id, String memo, LocalDateTime now) {
		RaceDocumentSyncEvent event = raceDocumentSyncEventRepository.findById(id)
			.orElseThrow(() -> new RaceSyncEventNotFoundException(id));
		event.completeManually(now, memo);
		slackNotifier.sendAfterCommit(RaceSyncAlert.completedText(RaceSyncType.DOCUMENT, event,
			event.getCompleteMemo()));
		return event;
	}
}
