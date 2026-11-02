package com.bukang.product.app;

import static com.bukang.common.shared.race.domain.RaceSaleStatus.CANCELED;
import static com.bukang.common.shared.race.domain.RaceSaleStatus.CLOSED;
import static com.bukang.common.shared.race.domain.RaceSaleStatus.ON_SALE;
import static com.bukang.common.shared.race.domain.RaceSaleStatus.SCHEDULED;
import static com.bukang.common.shared.race.domain.RaceSaleStatus.STOPPED;
import static com.bukang.product.support.RaceRequests.course;
import static com.bukang.product.support.RaceRequests.race;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.common.shared.race.event.RaceChangedEvent;
import com.bukang.product.domain.CourseType;
import com.bukang.product.support.JsonIds;
import com.bukang.product.support.ProductIntegrationTest;
import com.bukang.product.support.RaceRequests.CourseBuilder;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 판매 상태 배치(RaceSaleStatusUseCase.refresh)가 배치 시각에 맞춰 대회 판매 상태를 바꾸는지 확인한다.
 * - 판매 시작일이 된 판매 예정 → 판매 중 (판매 시작일 0시부터. 개인 예매 오픈 시각 전이어도 바뀐다)
 * - 대회 날짜가 지난 판매 예정·판매 중 → 판매 종료 (대회 당일은 그대로, 다음 날 0시부터). 둘 다 지났으면 바로 판매 종료
 * - 판매 중지·대회 취소는 건드리지 않고, 상태가 바뀐 대회의 변경 메시지만 바뀐 상태로 돌려준다
 * - ProductFacade.refreshSaleStatuses가 바뀐 대회의 검색 문서 동기화 이벤트를 남기고, 이벤트를 처리하면 공개 목록에 반영된다
 * 스케줄러(RaceSaleStatusScheduler)는 test 프로파일에서 돌지 않으므로, refresh에 원하는 시각을 넣어 직접 부른다.
 */
class RaceSaleStatusUseCaseTest extends ProductIntegrationTest {
	@Autowired
	private RaceSaleStatusUseCase raceSaleStatusUseCase;

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	@DisplayName("배치 시각이 4일 뒤면 판매 시작일이 3일 뒤인 판매 예정 대회는 판매 중이 되고, 5일 뒤인 대회는 그대로다")
	void openScheduledRaceWhoseSaleOpenDateCame() throws Exception {
		LocalDateTime base = LocalDateTime.now().withNano(0);
		RegisteredRace opening = register(race().title("사흘 뒤 판매 시작 대회")
			.individualOpenAt(base.plusDays(3))
			.build());
		RegisteredRace waiting = register(race().title("닷새 뒤 판매 시작 대회")
			.individualOpenAt(base.plusDays(5))
			.build());
		assertThat(saleStatusOf(opening.raceId())).isEqualTo(SCHEDULED);
		assertThat(saleStatusOf(waiting.raceId())).isEqualTo(SCHEDULED);
		LocalDateTime now = base.plusDays(4);

		List<RaceChangedEvent> changes = refresh(now);

		assertThat(saleStatusOf(opening.raceId())).isEqualTo(ON_SALE);
		assertThat(saleStatusOf(waiting.raceId())).isEqualTo(SCHEDULED);
		// 엔티티를 거치지 않는 UPDATE라 수정 일시를 배치 시각으로 직접 채운다
		assertThat(raceRepository.findById(opening.raceId()).orElseThrow().getModifyDate()).isEqualTo(now);
		assertThat(changes)
			.extracting(RaceChangedEvent::raceId, RaceChangedEvent::saleStatus)
			.containsExactly(tuple(opening.raceId(), ON_SALE));
	}

	@Test
	@DisplayName("개인 예매가 3일 뒤 10:00에 열리면 판매 시작일 전날 23:59:59에는 판매 예정 그대로이고, "
		+ "판매 시작일 0시에는 개인 예매 오픈 전이어도 판매 중이 된다")
	void openAtStartOfSaleOpenDate() throws Exception {
		LocalDate saleOpenDate = LocalDate.now().plusDays(3);
		RegisteredRace registered = register(race().individualOpenAt(saleOpenDate.atTime(10, 0)).build());

		assertThat(refresh(saleOpenDate.atStartOfDay().minusSeconds(1))).isEmpty();
		assertThat(saleStatusOf(registered.raceId())).isEqualTo(SCHEDULED);

		assertThat(refresh(saleOpenDate.atStartOfDay())).hasSize(1);
		assertThat(saleStatusOf(registered.raceId())).isEqualTo(ON_SALE);
	}

	@Test
	@DisplayName("판매 중 대회는 대회 당일 23:59:59에도 판매 중이고, 다음 날 0시에 판매 종료되며 수정 일시도 그 시각이 된다")
	void closeOnSaleRaceFromNextDay() throws Exception {
		LocalDate raceDay = LocalDate.now().plusDays(15);
		RegisteredRace registered = registerOnSale(race().raceDate(raceDay.atTime(9, 0)).build());
		assertThat(saleStatusOf(registered.raceId())).isEqualTo(ON_SALE);

		// 대회 당일에는 대회 시각(09:00)이 지났어도 판매 종료하지 않는다
		assertThat(refresh(raceDay.atTime(23, 59, 59))).isEmpty();
		assertThat(saleStatusOf(registered.raceId())).isEqualTo(ON_SALE);

		LocalDateTime nextDay = raceDay.plusDays(1).atStartOfDay();
		List<RaceChangedEvent> changes = refresh(nextDay);

		assertThat(saleStatusOf(registered.raceId())).isEqualTo(CLOSED);
		assertThat(raceRepository.findById(registered.raceId()).orElseThrow().getModifyDate()).isEqualTo(nextDay);
		assertThat(changes)
			.extracting(RaceChangedEvent::raceId, RaceChangedEvent::saleStatus)
			.containsExactly(tuple(registered.raceId(), CLOSED));
	}

	@Test
	@DisplayName("판매 시작일(3일 뒤)과 대회일(15일 뒤)이 모두 지난 판매 예정 대회는 판매 중을 거치지 않고 판매 종료된다")
	void closeScheduledRaceWithoutOpening() throws Exception {
		LocalDateTime base = LocalDateTime.now().withNano(0);
		LocalDate raceDay = base.toLocalDate().plusDays(15);
		RegisteredRace registered = register(race()
			.individualOpenAt(base.plusDays(3))
			.raceDate(raceDay.atTime(9, 0))
			.course(course(), fiveKm())
			.build());
		assertThat(saleStatusOf(registered.raceId())).isEqualTo(SCHEDULED);

		List<RaceChangedEvent> changes = refresh(raceDay.plusDays(1).atStartOfDay());

		assertThat(saleStatusOf(registered.raceId())).isEqualTo(CLOSED);
		// 대회 메시지는 판매 종료 1건뿐이다 (판매 중으로 만든 메시지가 섞이지 않는다)
		assertThat(changes)
			.extracting(RaceChangedEvent::raceId, RaceChangedEvent::saleStatus)
			.containsExactly(tuple(registered.raceId(), CLOSED));
	}

	@Test
	@DisplayName("판매 중지·대회 취소 대회는 판매 시작일과 대회일(15일 뒤)이 지난 30일 뒤에도 상태가 그대로다")
	void keepStoppedAndCanceledRaces() throws Exception {
		RegisteredRace stopped = registerOnSale(race().title("판매 중지 대회").build());
		// 판매 중지로 바꾸는 API가 아직 없어 상태 칸을 직접 바꾼다
		updateRace(stopped.raceId(), "saleStatus", STOPPED);
		RegisteredRace canceled = registerOnSale(race().title("취소된 대회").build());
		postAs("/api/v1/product/races/" + canceled.raceId() + "/cancel", SELLER_MEMBER_ID)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("대회를 취소했습니다."));

		List<RaceChangedEvent> changes = refresh(LocalDate.now().plusDays(30).atStartOfDay());

		assertThat(changes).isEmpty();
		assertThat(saleStatusOf(stopped.raceId())).isEqualTo(STOPPED);
		assertThat(saleStatusOf(canceled.raceId())).isEqualTo(CANCELED);
	}

	@Test
	@DisplayName("한 번 돌면 판매 중이 된 대회와 판매 종료된 대회의 메시지 2건만 바뀐 상태로 돌려준다")
	void returnChangesOfChangedRacesOnly() throws Exception {
		LocalDateTime base = LocalDateTime.now().withNano(0);
		LocalDate today = base.toLocalDate();
		RegisteredRace opening = register(race().title("사흘 뒤 판매 시작 대회")
			.individualOpenAt(base.plusDays(3))
			.course(course(), fiveKm())
			.build());
		RegisteredRace closing = registerOnSale(race().title("모레 열리는 대회")
			.raceDate(today.plusDays(2).atTime(9, 0))
			.applicationCloseAt(today.plusDays(1).atTime(18, 0))
			.build());
		RegisteredRace untouched = registerOnSale(race().title("보름 뒤 열리는 대회").build());
		assertThat(saleStatusOf(closing.raceId())).isEqualTo(ON_SALE);
		assertThat(saleStatusOf(untouched.raceId())).isEqualTo(ON_SALE);

		// 판매 시작(3일 뒤)과 모레 대회일은 지났고, 보름 뒤 대회일은 남은 시각
		List<RaceChangedEvent> changes = refresh(today.plusDays(4).atStartOfDay());

		assertThat(changes)
			.extracting(RaceChangedEvent::raceId, RaceChangedEvent::saleStatus)
			.containsExactlyInAnyOrder(
				tuple(opening.raceId(), ON_SALE),
				tuple(closing.raceId(), CLOSED));
		assertThat(saleStatusOf(opening.raceId())).isEqualTo(ON_SALE);
		assertThat(saleStatusOf(closing.raceId())).isEqualTo(CLOSED);
		assertThat(saleStatusOf(untouched.raceId())).isEqualTo(ON_SALE);
	}

	@Test
	@DisplayName("대회일이 이틀 지난 판매 중 대회는 상태 배치를 돌리고 동기화 이벤트를 처리하면 검색 문서가 판매 종료로 다시 색인되어 "
		+ "공개 목록에서 빠진다")
	void refreshSaleStatusesRemovesClosedRaceFromPublicList() throws Exception {
		RegisteredRace registered = registerOnSale(race().build());
		assertThat(publicRaceIds()).containsExactly(registered.raceId());
		// 대회일만 이틀 전으로 옮긴다. 검색 문서는 그대로라 아직 판매 중으로 공개 목록에 나온다
		// 접수 마감은 미래로 남겨, 공개 목록에서 빠지는 이유가 판매 상태뿐이게 한다
		// (등록 API로 대회일을 과거로 두면 접수 마감도 과거라, 판매 상태와 상관없이 처음부터 공개 목록에 없다)
		updateRace(registered.raceId(), "raceDate", LocalDateTime.now().withNano(0).minusDays(2));
		assertThat(publicRaceIds()).containsExactly(registered.raceId());

		int changed = productFacade.refreshSaleStatuses();
		// 상태 배치는 동기화 이벤트만 남긴다. 이벤트를 처리하기 전에는 검색 문서가 그대로다
		assertThat(documentStatusOf(registered.courseId())).isEqualTo(ON_SALE);
		productFacade.syncRaceDocuments();

		assertThat(changed).isEqualTo(1);
		assertThat(saleStatusOf(registered.raceId())).isEqualTo(CLOSED);
		assertThat(documentStatusOf(registered.courseId())).isEqualTo(CLOSED);
		assertThat(publicRaceIds()).doesNotContain(registered.raceId());
	}

	@Test
	@DisplayName("판매 시작일이 된 판매 예정 대회는 상태 배치를 돌리고 동기화 이벤트를 처리하면 검색 문서가 판매 중으로 다시 색인되고 "
		+ "공개 목록에 그대로 나온다")
	void refreshSaleStatusesReindexesOpenedRace() throws Exception {
		RegisteredRace registered = register(race()
			.individualOpenAt(LocalDateTime.now().plusDays(3))
			.build());
		assertThat(documentStatusOf(registered.courseId())).isEqualTo(SCHEDULED);
		assertThat(publicRaceIds()).containsExactly(registered.raceId());
		// 기다리지 않고 판매 시작일이 된 상태를 만들려고 판매 시작일만 오늘로 옮긴다
		updateRace(registered.raceId(), "saleOpenDate", LocalDate.now());

		int changed = productFacade.refreshSaleStatuses();
		productFacade.syncRaceDocuments();

		assertThat(changed).isEqualTo(1);
		assertThat(saleStatusOf(registered.raceId())).isEqualTo(ON_SALE);
		assertThat(documentStatusOf(registered.courseId())).isEqualTo(ON_SALE);
		assertThat(publicRaceIds()).containsExactly(registered.raceId());
	}

	// 원하는 배치 시각으로 판매 상태를 바꾼다. ProductFacade처럼 트랜잭션 안에서 부르고 커밋한다 (Kafka 아웃박스·동기화 이벤트는 남기지 않는다)
	private List<RaceChangedEvent> refresh(LocalDateTime now) {
		return transactionTemplate.execute(tx -> raceSaleStatusUseCase.refresh(now)).raceChanges();
	}

	// 기본 종목(10K) 옆에 붙이는 두 번째 종목
	private CourseBuilder fiveKm() {
		return course().name("5K").type(CourseType.KM_5).distanceKm("5").timeLimitMinutes(60);
	}

	private RaceSaleStatus saleStatusOf(long raceId) {
		return raceRepository.findById(raceId).orElseThrow().getSaleStatus();
	}

	private RaceSaleStatus documentStatusOf(long courseId) {
		return raceCourseDocumentRepository.findById(String.valueOf(courseId)).orElseThrow().getSaleStatus();
	}

	// 공개 목록(로그인 없음, 기본 정렬) 첫 페이지의 대회 ID
	private List<Long> publicRaceIds() throws Exception {
		String body = getAs("/api/v1/product/races", null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("대회 목록을 조회했습니다."))
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		return JsonIds.readLongs(body, "$.data.races[*].raceId");
	}

	// API로는 만들 수 없는 상태·일시를 만들려고 대회 행의 칸 하나를 직접 바꾼다 (검색 문서는 다시 색인하지 않는다)
	private void updateRace(long raceId, String field, Object value) {
		Integer updated = transactionTemplate.execute(tx -> entityManager
			.createQuery("update Race r set r." + field + " = :value where r.id = :id")
			.setParameter("value", value)
			.setParameter("id", raceId)
			.executeUpdate());
		assertThat(updated).isEqualTo(1);
	}
}
