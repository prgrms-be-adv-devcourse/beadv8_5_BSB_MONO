package com.bukang.product.app;

import static com.bukang.product.support.RaceRequests.course;
import static com.bukang.product.support.RaceRequests.race;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

import com.bukang.product.domain.BaseRaceSyncEvent;
import com.bukang.product.domain.CourseType;
import com.bukang.product.domain.RaceCourse;
import com.bukang.product.domain.SyncStatus;
import com.bukang.product.dto.RaceSaveDto;
import com.bukang.product.out.RaceSearchRepository;
import com.bukang.product.out.RaceSearchRepository.RaceDocumentCount;
import com.bukang.product.support.JsonIds;
import com.bukang.product.support.ProductIntegrationTest;
import com.bukang.product.support.RaceRequests.CourseBuilder;
import com.bukang.product.support.RaceRequests.RaceBuilder;

/**
 * 대회 저장 → 검색 문서·AI 추천 벡터 동기화 → 검색 문서 맞추기가 함께 맞물리는지 확인한다 (BUK-162).
 * 임베딩은 테스트용 Ollama 컨테이너(TestcontainersConfiguration)를 실제로 부른다. Docker가 없으면 건너뛴다.
 * 스케줄러는 테스트에서 돌지 않으므로 ProductFacade의 동기화·맞추기 메서드를 직접 부른다.
 * CI에서는 Ollama 컨테이너를 띄우지 않으므로 건너뛴다 (TestcontainersConfiguration.ollamaContainer).
 */
@DisabledIfEnvironmentVariable(named = "CI", matches = "true",
	disabledReason = "CI에서는 Ollama 컨테이너(8GB 이상)를 띄우지 않는다")
class RaceSearchSyncTest extends ProductIntegrationTest {
	@Autowired
	private RaceSearchRepository raceSearchRepository;

	private final LocalDateTime now = LocalDateTime.now().withNano(0);

	@Test
	@DisplayName("대회를 등록하면 문서 동기화·임베딩 동기화 표에 그 대회의 미처리 행이 1개씩 남는다")
	void registerAppendsBothSyncEvents() throws Exception {
		// given: register()는 문서 동기화까지 마치므로, 처리 전 상태를 보려고 등록 API를 직접 부른다
		// when
		long raceId = postScheduledRace(scheduledRace());

		// then
		assertSoftly(softly -> {
			softly.assertThat(documentSyncStatuses(raceId)).containsExactly(SyncStatus.PENDING);
			softly.assertThat(embeddingSyncStatuses(raceId)).containsExactly(SyncStatus.PENDING);
		});
	}

	@Test
	@DisplayName("문서 동기화와 임베딩 동기화를 처리하면 종목마다 검색 문서가 생기고, 모든 문서에 AI 추천 벡터가 들어가며, "
		+ "두 행 모두 처리 완료가 된다")
	void syncFillsDocumentsAndEmbeddings() throws Exception {
		// given: 종목 2개(10K·하프)인 대회를 등록한 직후
		long raceId = postScheduledRace(scheduledRace().course(tenK(), half()));

		// when
		productFacade.syncRaceDocuments();
		productFacade.syncRaceEmbeddings();

		// then
		assertSoftly(softly -> {
			softly.assertThat(documentCountOf(raceId)).isEqualTo(new RaceDocumentCount(2, 2));
			softly.assertThat(documentSyncStatuses(raceId)).containsExactly(SyncStatus.COMPLETE);
			softly.assertThat(embeddingSyncStatuses(raceId)).containsExactly(SyncStatus.COMPLETE);
		});
	}

	@Test
	@DisplayName("동기화 행도 검색 문서도 없이 DB에만 있는 대회는, 검색 문서 맞추기가 두 동기화 행을 남기고 "
		+ "처리하면 검색 문서와 벡터가 생긴다")
	void reconcileSyncsRaceOnlyInDb() throws Exception {
		// given: 등록한 뒤 두 동기화 행과 검색 문서를 지워, DB에 대회를 직접 넣은 것과 같은 상태로 만든다
		long raceId = register(scheduledRace().course(tenK(), half()).build()).raceId();
		raceDocumentSyncEventRepository.deleteAll();
		raceEmbeddingSyncEventRepository.deleteAll();
		raceCourseDocumentRepository.deleteAll();

		// when
		RaceSearchReconcileUseCase.Result result = productFacade.reconcileRaceSearch();
		productFacade.syncRaceDocuments();
		productFacade.syncRaceEmbeddings();

		// then
		assertSoftly(softly -> {
			softly.assertThat(result).isEqualTo(new RaceSearchReconcileUseCase.Result(1, 1));
			softly.assertThat(documentCountOf(raceId)).isEqualTo(new RaceDocumentCount(2, 2));
			softly.assertThat(documentSyncStatuses(raceId)).containsExactly(SyncStatus.COMPLETE);
			softly.assertThat(embeddingSyncStatuses(raceId)).containsExactly(SyncStatus.COMPLETE);
		});
	}

	@Test
	@DisplayName("10K만 있는 대회에 하프를 더하면 임베딩 동기화 행이 하나 더 남고, 처리하면 새 하프 문서에도 벡터가 들어간다")
	void addCourseFillsEmbeddingOfNewDocument() throws Exception {
		// given: 10K만 있는 대회의 문서와 벡터를 채운 상태
		long raceId = postScheduledRace(scheduledRace().course(tenK()));
		productFacade.syncRaceDocuments();
		productFacade.syncRaceEmbeddings();
		long tenKId = courseIdsOf(raceId).getFirst();

		// when: 하프를 더한다 (종목 구성이 바뀌어 임베딩할 글이 바뀐다)
		productFacade.updateRace(SELLER_MEMBER_ID, raceId,
			scheduledRace().course(tenK().courseId(tenKId), half()).build());
		productFacade.syncRaceDocuments();
		productFacade.syncRaceEmbeddings();

		// then
		assertSoftly(softly -> {
			softly.assertThat(embeddingSyncStatuses(raceId)).containsExactly(SyncStatus.COMPLETE, SyncStatus.COMPLETE);
			softly.assertThat(documentCountOf(raceId)).isEqualTo(new RaceDocumentCount(2, 2));
		});
	}

	@Test
	@DisplayName("참가비만 바꾼 수정은 임베딩할 글이 그대로라 임베딩 동기화 행을 남기지 않는다")
	void priceOnlyUpdateAppendsNoEmbeddingSync() throws Exception {
		// given: 등록 때 남은 임베딩 동기화 행 1개
		long raceId = postScheduledRace(scheduledRace().course(tenK()));
		long tenKId = courseIdsOf(raceId).getFirst();

		// when
		RaceSaveDto updated = productFacade.updateRace(SELLER_MEMBER_ID, raceId,
			scheduledRace().course(tenK().courseId(tenKId).price(55_000)).build());

		// then: 문서 동기화 행은 수정 때 하나 더 남지만, 임베딩 동기화 행은 등록 때의 1개뿐이다
		assertSoftly(softly -> {
			softly.assertThat(updated.getRaceId()).isEqualTo(raceId);
			softly.assertThat(documentSyncStatuses(raceId)).hasSize(2);
			softly.assertThat(embeddingSyncStatuses(raceId)).containsExactly(SyncStatus.PENDING);
		});
	}

	// 판매 예정 대회 (판매 시작일은 오늘로부터 이틀 뒤부터 등록할 수 있다). 판매 예정이어야 종목·참가비를 고칠 수 있다
	private RaceBuilder scheduledRace() {
		return race()
			.raceDate(now.plusDays(15))
			.individualOpenAt(now.plusDays(3))
			.applicationCloseAt(now.plusDays(10));
	}

	private CourseBuilder tenK() {
		return course().name("10K");
	}

	private CourseBuilder half() {
		return course().name("하프").type(CourseType.HALF).distanceKm("21.1").timeLimitMinutes(180);
	}

	// 등록 API만 부르고(동기화는 하지 않는다) 대회 ID를 돌려준다
	private long postScheduledRace(RaceBuilder race) throws Exception {
		String body = postRace(SELLER_MEMBER_ID, race.build())
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonIds.readLong(body, "$.data.raceId");
	}

	private List<Long> courseIdsOf(long raceId) {
		return raceCourseRepository.findAll().stream()
			.filter(raceCourse -> raceCourse.getRace().getId() == raceId)
			.map(RaceCourse::getId)
			.sorted()
			.toList();
	}

	// 그 대회의 검색 문서 수와 벡터가 있는 문서 수 (문서가 없으면 0, 0)
	private RaceDocumentCount documentCountOf(long raceId) {
		return raceSearchRepository.countDocumentsByRace()
			.getOrDefault(String.valueOf(raceId), new RaceDocumentCount(0, 0));
	}

	private List<SyncStatus> documentSyncStatuses(long raceId) {
		return statusesOf(raceDocumentSyncEventRepository.findAll(), raceId);
	}

	private List<SyncStatus> embeddingSyncStatuses(long raceId) {
		return statusesOf(raceEmbeddingSyncEventRepository.findAll(), raceId);
	}

	// 만든 순서대로 그 대회 행의 상태
	private static List<SyncStatus> statusesOf(List<? extends BaseRaceSyncEvent> events, long raceId) {
		return events.stream()
			.filter(event -> event.getRaceId() == raceId)
			.sorted((left, right) -> Long.compare(left.getId(), right.getId()))
			.map(BaseRaceSyncEvent::getStatus)
			.toList();
	}
}
