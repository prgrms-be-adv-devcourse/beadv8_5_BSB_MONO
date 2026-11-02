package com.bukang.product.app;

import static com.bukang.product.support.RaceRequests.course;
import static com.bukang.product.support.RaceRequests.race;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;

import com.bukang.product.domain.CourseType;
import com.bukang.product.domain.RaceDocumentSyncEvent;
import com.bukang.product.domain.SyncStatus;
import com.bukang.product.domain.document.RaceCourseDocument;
import com.bukang.product.out.RaceCourseDocumentRepository;
import com.bukang.product.support.JsonIds;
import com.bukang.product.support.ProductIntegrationTest;
import com.bukang.product.support.RaceRequests.CourseBuilder;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 대회 검색 문서 동기화 이벤트의 저장(RaceDocumentSyncAppendUseCase)과 처리(RaceDocumentSyncProcessUseCase)를 확인한다.
 * - 저장은 대회 저장 트랜잭션 안에서만 할 수 있고, 그 트랜잭션이 롤백되면 행도 남지 않는다
 * - 대회 저장 API는 이벤트만 남기고, 처리하면 검색 문서를 DB에 맞춘 뒤 COMPLETE로 바꾼다
 * - 같은 대회의 행이 여러 개여도 한 번만 처리하고, DB에 없는 종목의 문서만 지운다
 * - 처리하지 못하면 그 대회만 재시도 시각을 미루고(1초부터 두 배씩) 다른 대회는 계속 처리한다. MAX_RETRY번이면 FAILED
 * 검색 문서 저장소는 spy라 실제 ES에 저장하고, 실패는 대회별로 saveAll이 예외를 내게 바꿔 만든다.
 * 재시도 시각을 다루는 테스트는 처리에 시각을 직접 넘긴다 (DB가 소수 6자리로 저장하므로 초 단위로 맞춘 시각을 쓴다).
 */
class RaceDocumentSyncTest extends ProductIntegrationTest {
	@Autowired
	private RaceDocumentSyncAppendUseCase raceDocumentSyncAppendUseCase;
	@Autowired
	private RaceDocumentSyncProcessUseCase raceDocumentSyncProcessUseCase;
	@MockitoSpyBean
	private RaceCourseDocumentRepository documentRepositorySpy;
	@PersistenceContext
	private EntityManager entityManager;

	private final LocalDateTime now = LocalDateTime.now().withNano(0);

	@Test
	@DisplayName("대회 저장 트랜잭션이 롤백되면 동기화 이벤트도 남지 않는다")
	void rollbackWithBusinessTransaction() {
		transactionTemplate.executeWithoutResult(status -> {
			raceDocumentSyncAppendUseCase.append(101);
			status.setRollbackOnly();
		});

		assertThat(raceDocumentSyncEventRepository.count()).isZero();
	}

	@Test
	@DisplayName("트랜잭션 밖에서 동기화 이벤트를 저장하면 예외가 나고 행이 남지 않는다 (MANDATORY)")
	void rejectAppendOutsideTransaction() {
		assertThatThrownBy(() -> raceDocumentSyncAppendUseCase.append(101))
			.isInstanceOf(IllegalTransactionStateException.class);

		assertThat(raceDocumentSyncEventRepository.count()).isZero();
	}

	@Test
	@DisplayName("등록 API는 동기화 이벤트 1행(PENDING)만 남기고 검색 문서는 만들지 않으며, "
		+ "처리하면 문서가 생기고 행을 COMPLETE와 처리 시각으로 바꾼다 (문서 내용은 ApiV1RaceRegisterTest가 확인한다)")
	void registerLeavesPendingEventAndProcessSyncs() throws Exception {
		// register()는 문서를 맞추는 데까지 마치므로, 그 전 상태를 보려고 등록 API를 직접 부른다
		String body = postRace(SELLER_MEMBER_ID, race().individualOpenAt(now.plusDays(3)).build())
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		long raceId = JsonIds.readLong(body, "$.data.raceId");

		assertThat(eventsOf(raceId))
			.extracting(RaceDocumentSyncEvent::getStatus)
			.containsExactly(SyncStatus.PENDING);
		assertThat(documentIdsOf(raceId)).isEmpty();

		int synced = syncRaceDocuments();

		assertThat(synced).isEqualTo(1);
		assertThat(documentIdsOf(raceId)).isNotEmpty();
		assertThat(eventsOf(raceId)).allSatisfy(event -> {
			assertThat(event.getStatus()).isEqualTo(SyncStatus.COMPLETE);
			assertThat(event.getCompleteDate()).isNotNull();
			assertThat(event.getRetryCount()).isZero();
		});
	}

	@Test
	@DisplayName("같은 대회의 동기화 이벤트가 3행이면 한 번만 처리하고(saveAll 1번) 3행 모두 COMPLETE로 바꾼다")
	void syncOncePerRace() throws Exception {
		RegisteredRace registered = registerOnSale(race().build());
		append(registered.raceId());
		append(registered.raceId());
		append(registered.raceId());
		clearInvocations(documentRepositorySpy);

		int synced = processAt(now);

		assertThat(synced).isEqualTo(1);
		verify(documentRepositorySpy, times(1)).saveAll(documentsOf(registered.raceId()));
		assertThat(eventsOf(registered.raceId()))
			.allSatisfy(event -> assertThat(event.getStatus()).isEqualTo(SyncStatus.COMPLETE));
	}

	@Test
	@DisplayName("DB에서 직접 지운 하프 종목의 문서만 다음 처리 때 지우고, 남은 10K 문서와 다른 대회의 문서는 그대로 둔다")
	void deleteOnlyStaleDocuments() throws Exception {
		RegisteredRace registered = registerOnSale(race().course(course(), half()).build());
		RegisteredRace other = registerOnSale(race().title("다른 대회").build());
		long tenKmId = registered.courseIds().get(0);
		long halfId = registered.courseIds().get(1);
		// API를 거치지 않고 지워 ES에만 하프 문서가 남은 상태를 만든다 (예전에 지우지 못하고 남은 옛 문서)
		transactionTemplate.executeWithoutResult(status -> entityManager
			.createQuery("delete from RaceCourse c where c.id = :id")
			.setParameter("id", halfId)
			.executeUpdate());
		assertThat(documentIdsOf(registered.raceId())).contains(String.valueOf(halfId));

		resyncRaceDocument(registered.raceId());

		assertThat(documentIdsOf(registered.raceId()))
			.doesNotContain(String.valueOf(halfId))
			.containsExactly(String.valueOf(tenKmId));
		assertThat(documentIdsOf(other.raceId())).containsExactly(String.valueOf(other.courseId()));
	}

	@Test
	@DisplayName("대회 A의 처리가 실패하면 A의 행만 재시도 횟수 1, 재시도 시각 1초 뒤로 PENDING에 두고, 대회 B는 처리해 COMPLETE로 바꾼다")
	void failOnlyFailedRace() throws Exception {
		RegisteredRace raceA = registerOnSale(race().title("대회 A").build());
		RegisteredRace raceB = registerOnSale(race().title("대회 B").build());
		failSyncOf(raceA.raceId());
		append(raceA.raceId());
		append(raceB.raceId());

		int synced = processAt(now);

		assertThat(synced).isEqualTo(1); // 동기화 처리된건 B밖에 없음
		RaceDocumentSyncEvent failed = lastEventOf(raceA.raceId()); // 처리 실패한 이벤트행을 DB에서 꺼내옴
		assertThat(failed.getStatus()).isEqualTo(SyncStatus.PENDING); // 1번 실패는 아직 재시도 대상이라 PENDING
		assertThat(failed.getRetryCount()).isEqualTo(1);
		assertThat(failed.getNextRetryDate()).isEqualTo(now.plusSeconds(1)); // 첫 재시도 간격 1초
		assertThat(lastEventOf(raceB.raceId()).getStatus()).isEqualTo(SyncStatus.COMPLETE); // B처리 확인
	}

	@Test
	@DisplayName("MAX_RETRY번 처리하지 못하면 FAILED로 바꾸고, 다음 처리부터는 그 행을 읽지 않는다")
	void markFailedAfterMaxRetry() throws Exception {
		RegisteredRace registered = registerOnSale(race().build());
		failSyncOf(registered.raceId());
		append(registered.raceId());

		// 재시도 간격(최대 1분)이 늘 지나도록 한 시간씩 뒤의 시각으로 처리한다
		for (int i = 0; i < RaceDocumentSyncProcessUseCase.MAX_RETRY; i++) {
			processAt(now.plusHours(i));
		}
		clearInvocations(documentRepositorySpy);
		int synced = processAt(now.plusHours(RaceDocumentSyncProcessUseCase.MAX_RETRY));

		assertThat(synced).isZero();
		RaceDocumentSyncEvent failed = lastEventOf(registered.raceId());
		assertThat(failed.getStatus()).isEqualTo(SyncStatus.FAILED);
		assertThat(failed.getRetryCount()).isEqualTo(RaceDocumentSyncProcessUseCase.MAX_RETRY);
		verify(documentRepositorySpy, never()).saveAll(documentsOf(registered.raceId()));
	}

	private void append(long raceId) {
		transactionTemplate.executeWithoutResult(status -> raceDocumentSyncAppendUseCase.append(raceId));
	}

	// 트랜잭션 안에서 정한 시각으로 처리한다 (ProductFacade.syncRaceDocuments는 지금 시각을 쓴다)
	private int processAt(LocalDateTime time) {
		return transactionTemplate.execute(status -> raceDocumentSyncProcessUseCase.process(time));
	}

	// 이 대회의 문서를 저장하면 ES가 거절한 것처럼 예외를 낸다
	private void failSyncOf(long raceId) {
		doThrow(new IllegalStateException("ES 응답 없음")).when(documentRepositorySpy).saveAll(documentsOf(raceId));
	}

	// saveAll에 넘긴 문서가 이 대회의 문서인지
	private static Iterable<RaceCourseDocument> documentsOf(long raceId) {
		return argThat(documents -> documents != null && StreamSupport.stream(documents.spliterator(), false)
			.allMatch(document -> document.getRaceId().equals(String.valueOf(raceId))));
	}

	private List<String> documentIdsOf(long raceId) {
		return documentRepositorySpy.findByRaceId(String.valueOf(raceId)).stream()
			.map(RaceCourseDocument::getId)
			.toList();
	}

	// 이 대회의 동기화 이벤트 (만든 순서대로)
	private List<RaceDocumentSyncEvent> eventsOf(long raceId) {
		return raceDocumentSyncEventRepository.findAll().stream()
			.filter(event -> event.getRaceId() == raceId)
			.sorted((left, right) -> Long.compare(left.getId(), right.getId()))
			.toList();
	}

	private RaceDocumentSyncEvent lastEventOf(long raceId) {
		return eventsOf(raceId).getLast();
	}

	private static CourseBuilder half() {
		return course().name("하프").type(CourseType.HALF).distanceKm("21.1").timeLimitMinutes(180);
	}
}
