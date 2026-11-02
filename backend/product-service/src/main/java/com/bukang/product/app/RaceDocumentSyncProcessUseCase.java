package com.bukang.product.app;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import com.bukang.product.domain.Race;
import com.bukang.product.domain.RaceDocumentSyncEvent;
import com.bukang.product.domain.SyncStatus;
import com.bukang.product.domain.document.RaceCourseDocument;
import com.bukang.product.out.RaceCourseDocumentRepository;
import com.bukang.product.out.RaceDocumentSyncEventRepository;
import com.bukang.product.out.RaceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 미처리(PENDING) 동기화 이벤트를 대회별로 묶어 검색 문서(race_courses)를 DB에 맞춘다.
 * DB의 최신 대회·종목으로 문서를 맞추므로 같은 대회의 행이 여러 개여도 한 번만 처리하고, 그 행을 모두 COMPLETE로 바꾼다.
 * 1. DB에서 대회와 종목을 다시 읽어 종목마다 문서를 덮어쓴다
 * 2. ES에 있던 이 대회의 문서 중 DB에 없는 종목(수정 때 뺀 종목)의 문서만 지운다
 * 모두 지우고 다시 넣지 않으므로, 처리하는 동안에도 검색 결과에서 대회가 사라지지 않는다.
 * 순서가 상관없으므로 한 대회가 실패해도 다른 대회는 계속 처리한다 (Kafka 아웃박스처럼 key를 막지 않는다).
 * 실패한 대회의 행은 다시 처리할 때까지 간격을 1초 → 2초 → 4초 …로 늘리고(최대 1분), MAX_RETRY번 실패하면 FAILED로 바꾼다.
 * 재시도 시각이 안 된 대회도 그 사이 새로 바뀌어 행이 생겼으면 바로 처리한다 (새 행은 재시도 시각이 없다).
 * 한 트랜잭션에서 처리하므로, 행을 읽은 뒤 커밋된 대회 변경은 이번 처리에 빠질 수 있다. 그 변경이 남긴 행이 다음 실행에 다시 처리한다.
 * 앱을 여러 대 띄우면 같은 행을 함께 처리할 수 있다. 결과가 같아 문제는 없고, 지금은 한 대만 띄우므로 행을 잠그지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RaceDocumentSyncProcessUseCase {
	// 한 번에 읽을 최대 건수 (DB 부하 제한)
	static final int BATCH_SIZE = 100;
	// 이 횟수만큼 처리하지 못하면 FAILED로 바꾸고 더는 처리하지 않는다 (재시도 간격을 합치면 약 4분 동안 버틴다)
	static final int MAX_RETRY = 10;
	// 첫 재시도 간격. 실패할 때마다 두 배로 늘린다
	static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(1);
	// 재시도 간격의 최댓값
	static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(1);

	private final RaceDocumentSyncEventRepository raceDocumentSyncEventRepository;
	private final RaceRepository raceRepository;
	private final RaceCourseDocumentRepository raceCourseDocumentRepository;

	// 문서를 맞춘 대회 수를 돌려준다. 트랜잭션은 ProductFacade가 연다 (상태 변경은 더티 체킹으로 커밋된다)
	public int process(LocalDateTime now) {
		List<RaceDocumentSyncEvent> events = raceDocumentSyncEventRepository
			.findByStatusOrderByCreateDateAscIdAsc(SyncStatus.PENDING, Limit.of(BATCH_SIZE));
		// 대회 ID → 그 대회의 행 (먼저 만든 대회부터)
		Map<Long, List<RaceDocumentSyncEvent>> eventsByRace = new LinkedHashMap<>();
		events.forEach(event ->
			eventsByRace.computeIfAbsent(event.getRaceId(), raceId -> new ArrayList<>()).add(event));

		int synced = 0;
		for (Map.Entry<Long, List<RaceDocumentSyncEvent>> entry : eventsByRace.entrySet()) {
			List<RaceDocumentSyncEvent> raceEvents = entry.getValue();
			if (raceEvents.stream().noneMatch(event -> event.isProcessableAt(now))) {
				continue;
			}
			if (!sync(entry.getKey())) {
				raceEvents.forEach(event -> markFailed(event, now));
				continue;
			}
			raceEvents.forEach(event -> event.markComplete(now));
			synced++;
		}
		return synced;
	}

	// failCount번째 실패 뒤 기다릴 시간: 1초, 2초, 4초, … 최대 1분
	static Duration retryDelay(int failCount) {
		int shift = Math.min(failCount - 1, 30);
		Duration delay = FIRST_RETRY_DELAY.multipliedBy(1L << shift);
		return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
	}

	// 대회 1개의 검색 문서를 DB의 최신 대회·종목에 맞춘다. 성공하면 true
	// 예외를 잡아 false로 돌려준다. 잡지 않으면 process의 트랜잭션이 롤백되어 실패 기록(재시도 횟수)도 저장되지 않는다
	private boolean sync(long raceId) {
		try {
			// 대회가 없으면(지워진 대회) 종목도 없는 것으로 보고 남은 문서를 모두 지운다
			List<RaceCourseDocument> documents = raceRepository.findWithCoursesById(raceId)
				.map(Race::getCourses)
				.orElse(List.of())
				.stream()
				.map(RaceCourseDocument::from)
				.toList();
			if (!documents.isEmpty()) {
				raceCourseDocumentRepository.saveAll(documents);
			}

			Set<String> currentIds = documents.stream().map(RaceCourseDocument::getId).collect(Collectors.toSet());
			List<String> staleIds = raceCourseDocumentRepository.findByRaceId(String.valueOf(raceId)).stream()
				.map(RaceCourseDocument::getId)
				.filter(id -> !currentIds.contains(id))
				.toList();
			if (!staleIds.isEmpty()) {
				raceCourseDocumentRepository.deleteAllById(staleIds);
			}
			return true;
		} catch (RuntimeException exception) {
			log.warn("대회 검색 문서를 DB에 맞추지 못했습니다. raceId={}", raceId, exception);
			return false;
		}
	}

	private void markFailed(RaceDocumentSyncEvent event, LocalDateTime now) {
		event.markFailed(MAX_RETRY, now.plus(retryDelay(event.getRetryCount() + 1)));
		if (event.isFailed()) {
			log.error("대회 검색 문서를 {}번 맞추지 못해 FAILED로 바꿨습니다. 원인을 고친 뒤 PENDING으로 되돌려야 합니다. "
				+ "id={}, raceId={}", MAX_RETRY, event.getId(), event.getRaceId());
		}
	}
}
