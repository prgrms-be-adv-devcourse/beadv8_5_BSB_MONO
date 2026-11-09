package com.bukang.product.app;

import static com.bukang.product.support.RaceRequests.course;
import static com.bukang.product.support.RaceRequests.race;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.bukang.product.domain.HoldReleaseReason;
import com.bukang.product.domain.HoldStatus;
import com.bukang.product.domain.ParticipationHold;
import com.bukang.product.domain.RaceCourse;
import com.bukang.product.support.ProductIntegrationTest;
import com.bukang.product.support.RaceRequests.RaceBuilder;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 크루 자리 매시간 배치(ProductFacade.runCrewSeatBatch)를 고정 시각으로 확인한다 (BUK-110)
 * 시나리오: 크루 예매 마감 11/12 19:00 (19:00부터 파티 참여 확정이 막히고, 결제 대기는 마감일의 자정 11/13 00:00까지 결제할 수 있다)
 * - 매시 실행: 결제 기한이 지난 결제 대기 크루 자리를 반환한다 (예: 기한 11/12 13:12 → 14:00 실행)
 * - 11/13 00:00 실행: 결제 대기 크루 자리를 모두 반환하고 남은 크루 자리를 개인 몫으로 옮긴다
 * 크루 자리는 확보 API로 잡고(지금 크루 예매 중인 대회), 크루 예매 마감·결제 기한만 시나리오의 고정 시각으로 행을 직접 바꾼다.
 * 배치는 넘긴 시각만 보므로 실제 지금 시각과 상관없이 시나리오대로 돈다.
 */
class CrewSeatBatchTest extends ProductIntegrationTest {
	private static final long PARTY_ID = 31;
	private static final long CREW_ID = 7;
	private static final long[] PARTY_MEMBERS = {1001, 1002, 1003};
	// 개인 예매만 하는 다른 대회의 참가 신청
	private static final long INDIVIDUAL_PARTICIPATION_ID = 2001;
	private static final int CREW_QUOTA = 5;
	private static final int INDIVIDUAL_QUOTA = 100;
	// 시나리오의 고정 시각
	private static final LocalDateTime CREW_SALE_END_AT = LocalDateTime.of(2026, 11, 12, 19, 0);
	private static final LocalDateTime CREW_CLOSE_AT = LocalDateTime.of(2026, 11, 13, 0, 0);

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	@DisplayName("11/11 13:12에 확정한 파티(결제 기한 11/12 13:12)의 결제 대기 크루 자리는 11/12 14:00 실행에서 기한 초과로 반환되고, "
		+ "크루 결제 대기가 0이 되어 남은 크루 자리로 돌아간다")
	void releaseExpiredCrewSeats() throws Exception {
		// given
		CrewCourse crewCourse = heldCrewCourse();
		setExpiresAt(LocalDateTime.of(2026, 11, 12, 13, 12), PARTY_MEMBERS);

		// when
		CrewSeatBatchUseCase.Result result = productFacade.runCrewSeatBatch(LocalDateTime.of(2026, 11, 12, 14, 0));

		// then
		RaceCourse course = courseOf(crewCourse.courseId());
		List<ParticipationHold> seats = participationHoldRepository.findByPartyIdOrderByParticipationId(PARTY_ID);
		assertSoftly(softly -> {
			softly.assertThat(result.released()).isEqualTo(3);
			softly.assertThat(seats).extracting(ParticipationHold::getStatus).containsOnly(HoldStatus.RELEASED);
			softly.assertThat(seats).extracting(ParticipationHold::getReleaseReason)
				.containsOnly(HoldReleaseReason.EXPIRED);
			softly.assertThat(course.getCrewReserved()).isZero();
			softly.assertThat(course.crewRemaining()).isEqualTo(CREW_QUOTA);
			softly.assertThat(course.getMovedQuota()).isZero();
		});
	}

	@Test
	@DisplayName("11/12 14:00 실행에서 결제 기한이 남은(15:00) 결제 대기 크루 자리와, 개인 예매만 하는 다른 대회의 기한 지난 개인 자리는 "
		+ "반환하지 않는다")
	void keepUnexpiredCrewSeatsAndIndividualSeats() throws Exception {
		// given: 크루 대회의 결제 대기 3석(기한 15:00)과, 개인 예매만 하는 다른 대회의 개인 결제 대기 1석(기한 13:00)
		// 같은 대회에서는 개인 예매가 크루 예매 마감일의 자정 뒤에 열려 크루 결제 대기와 함께 있을 수 없으므로 다른 대회에 둔다
		CrewCourse crewCourse = heldCrewCourse();
		setExpiresAt(LocalDateTime.of(2026, 11, 12, 15, 0), PARTY_MEMBERS);
		long individualCourseId = registerOnSale(race().build()).courseId();
		hold(individualCourseId, INDIVIDUAL_PARTICIPATION_ID).andExpect(status().isOk());
		setExpiresAt(LocalDateTime.of(2026, 11, 12, 13, 0), INDIVIDUAL_PARTICIPATION_ID);

		// when
		CrewSeatBatchUseCase.Result result = productFacade.runCrewSeatBatch(LocalDateTime.of(2026, 11, 12, 14, 0));

		// then
		assertSoftly(softly -> {
			softly.assertThat(result.released()).isZero();
			softly.assertThat(participationHoldRepository.findAll()).extracting(ParticipationHold::getStatus)
				.containsOnly(HoldStatus.HELD);
			softly.assertThat(courseOf(crewCourse.courseId()).getCrewReserved()).isEqualTo(3);
			softly.assertThat(courseOf(individualCourseId).getIndividualReserved()).isEqualTo(1);
		});
	}

	@Test
	@DisplayName("크루 예매가 19:00에 마감돼도, 17:00에 확정한 파티(결제 기한 11/13 00:00)의 결제 대기는 23:00 실행에서 그대로 남고 "
		+ "크루 몫도 옮기지 않는다")
	void keepCrewSeatsUntilCrewCloseMidnight() throws Exception {
		// given
		CrewCourse crewCourse = heldCrewCourse();
		setExpiresAt(CREW_CLOSE_AT, PARTY_MEMBERS);

		// when
		CrewSeatBatchUseCase.Result result = productFacade.runCrewSeatBatch(LocalDateTime.of(2026, 11, 12, 23, 0));

		// then
		RaceCourse course = courseOf(crewCourse.courseId());
		assertSoftly(softly -> {
			softly.assertThat(result.released()).isZero();
			softly.assertThat(result.closedCourses()).isZero();
			softly.assertThat(course.getCrewReserved()).isEqualTo(3);
			softly.assertThat(course.getCrewClosedAt()).isNull();
			softly.assertThat(course.getMovedQuota()).isZero();
		});
	}

	@Test
	@DisplayName("11/13 00:00 실행에서 결제 대기 크루 자리(1002, 1003)는 반환되고, 남은 크루 자리 4석이 개인 몫으로 넘어간다. "
		+ "결제한 크루 자리(1001)는 판매로 남는다")
	void closeCrewAtCrewCloseMidnight() throws Exception {
		// given: 크루 5석 중 1001은 결제했고, 1002·1003은 17:00 확정이라 결제 기한이 11/13 00:00이다
		CrewCourse crewCourse = heldCrewCourse();
		sell(1001).andExpect(status().isOk());
		setExpiresAt(CREW_CLOSE_AT, 1002, 1003);

		// when
		CrewSeatBatchUseCase.Result result = productFacade.runCrewSeatBatch(CREW_CLOSE_AT);

		// then
		RaceCourse course = courseOf(crewCourse.courseId());
		assertSoftly(softly -> {
			softly.assertThat(result.released()).isEqualTo(2);
			softly.assertThat(result.closedCourses()).isEqualTo(1);
			softly.assertThat(holdOf(1001).getStatus()).isEqualTo(HoldStatus.SOLD);
			softly.assertThat(holdOf(1002).getStatus()).isEqualTo(HoldStatus.RELEASED);
			softly.assertThat(holdOf(1003).getStatus()).isEqualTo(HoldStatus.RELEASED);
			softly.assertThat(course.getCrewReserved()).isZero();
			softly.assertThat(course.getCrewSold()).isEqualTo(1);
			softly.assertThat(course.getMovedQuota()).isEqualTo(CREW_QUOTA - 1);
			softly.assertThat(course.crewRemaining()).isZero();
			softly.assertThat(course.individualRemaining()).isEqualTo(INDIVIDUAL_QUOTA + CREW_QUOTA - 1);
			softly.assertThat(course.getCrewClosedAt()).isEqualTo(CREW_CLOSE_AT);
		});
	}

	// ---------- 도우미 ----------

	private record CrewCourse(long raceId, long courseId) {
	}

	// 크루 5석·개인 100석 종목에 파티원 3명(1001~1003)의 크루 자리를 잡고, 크루 예매 마감을 시나리오의 11/12 19:00으로 바꾼다
	// 확보 API는 지금 크루 예매 중이어야 받으므로, 지금 크루 예매 중인 대회로 잡은 뒤 마감 일시만 바꾼다
	private CrewCourse heldCrewCourse() throws Exception {
		RaceBuilder builder = race();
		LocalDateTime now = builder.now();
		LocalDateTime crewSaleEndAt = now.plusDays(1);
		RegisteredRace registered = registerOnSale(builder
			.crewSale(now.minusHours(1), crewSaleEndAt)
			.individualOpenAt(crewSaleEndAt.toLocalDate().plusDays(1).atStartOfDay())
			.course(course().quota(CREW_QUOTA, INDIVIDUAL_QUOTA))
			.build());
		CrewCourse crewCourse = new CrewCourse(registered.raceId(), registered.courseId());
		holdCrew(crewCourse, now.plusDays(1).withNano(0), PARTY_MEMBERS);
		transactionTemplate.executeWithoutResult(status -> entityManager
			.createQuery("update Race r set r.crewSaleEndAt = :crewSaleEndAt where r.id = :id")
			.setParameter("crewSaleEndAt", CREW_SALE_END_AT)
			.setParameter("id", crewCourse.raceId())
			.executeUpdate());
		return crewCourse;
	}

	// 주문 서비스가 보내는 크루 자리 확보 요청 (파티원마다 참가 신청 ID와 같은 종목)
	private void holdCrew(CrewCourse crewCourse, LocalDateTime expiresAt, long... participationIds) throws Exception {
		String seats = Arrays.stream(participationIds)
			.mapToObj(id -> "{\"participationId\":%d,\"raceCourseId\":%d}".formatted(id, crewCourse.courseId()))
			.collect(Collectors.joining(","));
		String body = "{\"partyId\":%d,\"crewId\":%d,\"raceId\":%d,\"expiresAt\":\"%s\",\"seats\":[%s]}"
			.formatted(PARTY_ID, CREW_ID, crewCourse.raceId(), expiresAt, seats);
		mockMvc.perform(post("/internal/v1/product/crew-holds")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isOk());
	}

	// 결제 기한을 시나리오 시각으로 바꾼다 (주문이 확정 시각으로 계산해 보낸 값이라고 본다)
	private void setExpiresAt(LocalDateTime expiresAt, long... participationIds) {
		List<Long> ids = Arrays.stream(participationIds).boxed().toList();
		transactionTemplate.executeWithoutResult(status -> entityManager
			.createQuery("update ParticipationHold h set h.expiresAt = :expiresAt where h.participationId in :ids")
			.setParameter("expiresAt", expiresAt)
			.setParameter("ids", ids)
			.executeUpdate());
	}

	private RaceCourse courseOf(long raceCourseId) {
		return raceCourseRepository.findById(raceCourseId).orElseThrow();
	}

	private ParticipationHold holdOf(long participationId) {
		return participationHoldRepository.findByParticipationId(participationId).orElseThrow();
	}
}
