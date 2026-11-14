package com.bukang.product.app;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import com.bukang.common.global.notify.SlackNotifier;
import com.bukang.product.domain.Race;
import com.bukang.product.domain.RaceEmbeddingSyncEvent;
import com.bukang.product.domain.RaceSyncType;
import com.bukang.product.domain.SyncStatus;
import com.bukang.product.out.RaceCourseDocumentRepository;
import com.bukang.product.out.RaceEmbeddingSyncEventRepository;
import com.bukang.product.out.RaceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 미처리(PENDING) 임베딩 동기화 이벤트를 대회별로 묶어 검색 문서의 벡터 칸(raceEmbedding)을 채운다 (BUK-162).
 * 1. DB에서 대회와 종목을 다시 읽어 임베딩할 글(Race.embeddingText)을 만든다
 * 2. 임베딩 모델을 대회당 한 번 불러 벡터 1개를 받는다 (RaceTextEmbedder, 차원은 AiConfig.embeddingOptions로 넘긴다)
 * 3. 그 대회의 종목 문서 모두에 같은 벡터를 넣는다. 벡터 칸만 고치고 문서를 새로 만들지 않는다
 * 같은 대회의 행이 여러 개여도 DB의 최신 글로 한 번만 임베딩하고, 그 행을 모두 COMPLETE로 바꾼다.
 * 문서가 아직 없으면(문서 동기화보다 먼저 돈 경우) 실패로 보고 다시 시도한다. 문서 동기화가 1초마다 돌아 곧 문서가 생긴다.
 * 재시도 간격은 문서 동기화(1초부터)보다 길게 5초부터 두 배씩, 최대 5분으로 둔다.
 * 임베딩은 외부 API(OpenAI·Ollama)라 잠깐 끊기면 몇 분 이어질 수 있어서다. MAX_RETRY번 실패하면 FAILED로 바꾼다 (약 20분 버틴다).
 * FAILED가 되면 Slack으로 알리고, 그 행을 사람이 되돌려 처리되면 완료도 알린다 (RaceSyncAlert).
 * 임베딩 API를 트랜잭션 안에서 부르므로, 한 번에 처리하는 대회 수(BATCH_SIZE)를 작게 둔다 (DB 연결을 오래 잡지 않게).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RaceEmbeddingSyncProcessUseCase {
	// 한 번에 읽을 최대 건수. 대회마다 임베딩 API를 한 번 부르므로 문서 동기화(100)보다 작게 둔다
	static final int BATCH_SIZE = 20;
	// 이 횟수만큼 처리하지 못하면 FAILED로 바꾸고 더는 처리하지 않는다
	static final int MAX_RETRY = 10;
	// 첫 재시도 간격. 실패할 때마다 두 배로 늘린다
	static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(5);
	// 재시도 간격의 최댓값
	static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(5);

	private final RaceEmbeddingSyncEventRepository raceEmbeddingSyncEventRepository;
	private final RaceRepository raceRepository;
	private final RaceCourseDocumentRepository raceCourseDocumentRepository;
	private final RaceTextEmbedder raceTextEmbedder;
	private final SlackNotifier slackNotifier;

	// 벡터를 채운 대회 수를 돌려준다. 트랜잭션은 ProductFacade가 연다 (상태 변경은 더티 체킹으로 커밋된다)
	public int process(LocalDateTime now) {
		List<RaceEmbeddingSyncEvent> events = raceEmbeddingSyncEventRepository
			.findByStatusOrderByCreateDateAscIdAsc(SyncStatus.PENDING, Limit.of(BATCH_SIZE));
		// 대회 ID → 그 대회의 행 (먼저 만든 대회부터)
		Map<Long, List<RaceEmbeddingSyncEvent>> eventsByRace = new LinkedHashMap<>();
		events.forEach(event ->
			eventsByRace.computeIfAbsent(event.getRaceId(), raceId -> new ArrayList<>()).add(event));

		int synced = 0;
		for (Map.Entry<Long, List<RaceEmbeddingSyncEvent>> entry : eventsByRace.entrySet()) {
			List<RaceEmbeddingSyncEvent> raceEvents = entry.getValue();
			if (raceEvents.stream().noneMatch(event -> event.isProcessableAt(now))) {
				continue;
			}
			if (!sync(entry.getKey())) {
				raceEvents.forEach(event -> markFailed(event, now));
				continue;
			}
			raceEvents.forEach(event -> markComplete(event, now));
			synced++;
		}
		return synced;
	}

	// failCount번째 실패 뒤 기다릴 시간: 5초, 10초, 20초, … 최대 5분
	static Duration retryDelay(int failCount) {
		int shift = Math.min(failCount - 1, 30);
		Duration delay = FIRST_RETRY_DELAY.multipliedBy(1L << shift);
		return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
	}

	// 대회 1개의 종목 문서에 벡터를 넣는다. 성공하면 true
	// 예외를 잡아 false로 돌려준다. 잡지 않으면 process의 트랜잭션이 롤백되어 실패 기록(재시도 횟수)도 저장되지 않는다
	private boolean sync(long raceId) {
		try {
			Optional<Race> found = raceRepository.findWithCoursesById(raceId);
			// 지워진 대회나 종목이 없는 대회는 벡터를 넣을 문서가 없으므로 할 일 없이 끝낸다
			if (found.isEmpty() || found.get().getCourses().isEmpty()) {
				return true;
			}
			Race race = found.get();
			float[] embedding = raceTextEmbedder.embed(race.embeddingText());
			// 벡터를 넣을 문서는 ES에서 찾지 않고 DB의 종목 ID로 정한다
			// (ES에서 찾으면 문서 동기화 전에는 0개라 할 일 없이 끝난 것으로 잘못 볼 수 있다)
			List<String> documentIds = race.getCourses().stream()
				.map(course -> String.valueOf(course.getId()))
				.toList();
			raceCourseDocumentRepository.updateEmbedding(documentIds, embedding);
			return true;
		} catch (RuntimeException exception) {
			// 임베딩할 글(대회 소개 등)은 로그에 남기지 않는다
			log.warn("대회 AI 추천 벡터를 만들지 못했습니다. raceId={}, 원인={}", raceId, exception.toString());
			return false;
		}
	}

	// 실패 알림이 간 행(재처리 API나 DB로 되돌린 행)이 처리되면 커밋 뒤 Slack으로 완료를 알린다
	private void markComplete(RaceEmbeddingSyncEvent event, LocalDateTime now) {
		event.markComplete(now);
		if (event.hasFailed()) {
			slackNotifier.sendAfterCommit(RaceSyncAlert.completedText(RaceSyncType.EMBEDDING, event,
				RaceSyncAlert.AUTO_COMPLETE_MEMO));
		}
	}

	// FAILED가 되면 커밋 뒤 Slack으로 알린다
	private void markFailed(RaceEmbeddingSyncEvent event, LocalDateTime now) {
		boolean failedBefore = event.hasFailed();
		event.markFailed(MAX_RETRY, now.plus(retryDelay(event.getRetryCount() + 1)), now);
		if (event.isFailed()) {
			log.error("대회 AI 추천 벡터를 {}번 만들지 못해 FAILED로 바꿨습니다. 원인을 고친 뒤 재처리 API로 PENDING으로 되돌려야 합니다. "
				+ "id={}, raceId={}", MAX_RETRY, event.getId(), event.getRaceId());
			slackNotifier.sendAfterCommit(RaceSyncAlert.failedText(RaceSyncType.EMBEDDING, event, failedBefore));
		}
	}
}
