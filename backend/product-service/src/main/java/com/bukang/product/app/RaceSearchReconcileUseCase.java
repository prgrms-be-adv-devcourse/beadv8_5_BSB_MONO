package com.bukang.product.app;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.product.domain.SyncStatus;
import com.bukang.product.out.RaceDocumentSyncEventRepository;
import com.bukang.product.out.RaceEmbeddingSyncEventRepository;
import com.bukang.product.out.RaceRepository;
import com.bukang.product.out.RaceRepository.RaceCourseCount;
import com.bukang.product.out.RaceSearchRepository;
import com.bukang.product.out.RaceSearchRepository.RaceDocumentCount;

import lombok.RequiredArgsConstructor;

/**
 * DB의 대회와 검색 문서(race_courses)를 견줘, 어긋난 대회에 문서·임베딩 동기화 이벤트를 남긴다 (검색 문서 맞추기, BUK-162).
 * 이벤트는 대회를 저장할 때만 생겨서, 아래처럼 저장 없이 어긋난 대회는 그대로 남는다. 이 유스케이스가 그 대회를 찾아 다시 맞추게 한다.
 * - ES에 문서가 없거나 종목 수와 다름: ES 데이터가 날아감, 인덱스를 다시 만듦 → 문서 동기화 이벤트
 * - 문서는 있는데 벡터가 없는 문서가 있음: 벡터 칸을 더하기 전부터 있던 대회(백필) → 임베딩 동기화 이벤트
 * 행만 남기고, 실제 처리는 두 동기화 스케줄러가 한다. 문서와 벡터가 둘 다 없으면 둘 다 남긴다
 * (임베딩이 먼저 돌면 문서가 없어 다시 시도하다가, 문서가 생긴 뒤 채운다).
 * 대상은 검색·추천에 나올 수 있는 대회(판매 예정·판매 중·판매 중지)다. 취소·판매 종료 대회는 맞추지 않는다.
 * 그 동기화 표에 이미 처리 중(PENDING)이거나 실패한(FAILED) 행이 있는 대회는 건너뛴다.
 * 처리 중이면 곧 맞춰지고, 실패한 대회는 원인을 고친 사람이 되돌릴 때까지 다시 시도하지 않는다 (같은 실패를 되풀이하지 않게).
 */
@Service
@RequiredArgsConstructor
public class RaceSearchReconcileUseCase {
	private static final List<RaceSaleStatus> TARGET_STATUSES = List.of(
		RaceSaleStatus.SCHEDULED, RaceSaleStatus.ON_SALE, RaceSaleStatus.STOPPED);
	private static final List<SyncStatus> IN_PROGRESS_OR_FAILED = List.of(SyncStatus.PENDING, SyncStatus.FAILED);

	private final RaceRepository raceRepository;
	private final RaceSearchRepository raceSearchRepository;
	private final RaceDocumentSyncEventRepository raceDocumentSyncEventRepository;
	private final RaceEmbeddingSyncEventRepository raceEmbeddingSyncEventRepository;
	private final RaceDocumentSyncAppendUseCase raceDocumentSyncAppendUseCase;
	private final RaceEmbeddingSyncAppendUseCase raceEmbeddingSyncAppendUseCase;

	// 이번에 이벤트를 남긴 대회 수 (문서 동기화, 임베딩 동기화)
	public record Result(int documentSyncAppended, int embeddingSyncAppended) {
		public boolean isEmpty() {
			return documentSyncAppended == 0 && embeddingSyncAppended == 0;
		}
	}

	// 트랜잭션은 ProductFacade가 연다 (두 append 유스케이스가 MANDATORY다)
	public Result reconcile() {
		List<RaceCourseCount> races = raceRepository.countCoursesBySaleStatusIn(TARGET_STATUSES);
		Map<String, RaceDocumentCount> documents = raceSearchRepository.countDocumentsByRace();
		Set<Long> skipDocumentSync = new HashSet<>(
			raceDocumentSyncEventRepository.findRaceIdsByStatusIn(IN_PROGRESS_OR_FAILED));
		Set<Long> skipEmbeddingSync = new HashSet<>(
			raceEmbeddingSyncEventRepository.findRaceIdsByStatusIn(IN_PROGRESS_OR_FAILED));

		int documentSyncAppended = 0;
		int embeddingSyncAppended = 0;
		for (RaceCourseCount race : races) {
			long raceId = race.getRaceId();
			RaceDocumentCount count = documents.getOrDefault(String.valueOf(raceId), new RaceDocumentCount(0, 0));
			// 문서 수가 종목 수와 다르면 문서를 다시 맞춘다 (없거나 모자람, 지운 종목의 문서가 남음)
			if (count.documents() != race.getCourseCount() && !skipDocumentSync.contains(raceId)) {
				raceDocumentSyncAppendUseCase.append(raceId);
				documentSyncAppended++;
			}
			// 종목마다 벡터가 있어야 한다. 모자라면 벡터를 다시 만든다 (문서가 없어 0인 경우도 포함)
			if (count.withEmbedding() < race.getCourseCount() && !skipEmbeddingSync.contains(raceId)) {
				raceEmbeddingSyncAppendUseCase.append(raceId);
				embeddingSyncAppended++;
			}
		}
		return new Result(documentSyncAppended, embeddingSyncAppended);
	}
}
