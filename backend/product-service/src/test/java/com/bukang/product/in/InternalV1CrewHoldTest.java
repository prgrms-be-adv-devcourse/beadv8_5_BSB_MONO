package com.bukang.product.in;

import static com.bukang.product.support.RaceRequests.course;
import static com.bukang.product.support.RaceRequests.race;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.bukang.product.domain.ParticipationHold;
import com.bukang.product.domain.RaceCourse;
import com.bukang.product.support.ProductIntegrationTest;
import com.bukang.product.support.RaceRequests.RaceBuilder;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 주문 서비스가 파티장의 [대회 참가] 때 부르는 크루 자리 확보 내부 API(InternalV1CrewHoldController)와,
 * 크루로 잡은 자리의 판매·해제·복구가 크루 몫 카운터를 바꾸는지 확인한다.
 * 대회는 지금 크루 예매 기간(1시간 전 ~ 내일)인 판매 중 대회이고, 파티는 종목 하나로 신청한다(파티원 모두 같은 종목).
 * 해제·복구는 주문 서비스가 Kafka로 알리므로 리스너 메서드에 본문을 직접 넘긴다.
 */
class InternalV1CrewHoldTest extends ProductIntegrationTest {
	private static final String CREW_HOLDS_PATH = "/internal/v1/product/crew-holds";
	private static final long PARTY_ID = 31;
	private static final long OTHER_PARTY_ID = 32;
	private static final long CREW_ID = 7;
	// 파티원 3명의 참가 신청
	private static final long[] PARTY_MEMBERS = {1001, 1002, 1003};
	private static final long[] OTHER_PARTY_MEMBERS = {2001, 2002, 2003};
	// 개인 정원은 크루 카운터와 섞이지 않았는지 보려고 둔다
	private static final int INDIVIDUAL_QUOTA = 100;

	@Autowired
	private ProductKafkaConsumer productKafkaConsumer;

	@PersistenceContext
	private EntityManager entityManager;

	// ---------- 확보 ----------

	@Test
	@DisplayName("남은 크루 자리 5석인 종목에 3명 파티가 확보하면 held=true·shortages=[]이고, "
		+ "파티·크루 ID가 붙은 결제 대기 자리 3석이 생기며 크루 결제 대기는 3이다")
	void holdCrewSeats() throws Exception {
		// given
		CrewCourse crewCourse = crewCourseWithQuota(5);
		LocalDateTime expiresAt = tomorrow();

		// when
		ResultActions result = holdCrew(PARTY_ID, crewCourse, expiresAt, PARTY_MEMBERS);

		// then
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("크루 자리를 확보했습니다."))
			.andExpect(jsonPath("$.data.held").value(true))
			.andExpect(jsonPath("$.data.shortages").isEmpty());
		RaceCourse course = courseOf(crewCourse.courseId());
		List<ParticipationHold> seats = participationHoldRepository.findByPartyIdOrderByParticipationId(PARTY_ID);
		assertSoftly(softly -> {
			softly.assertThat(seats).extracting(ParticipationHold::getParticipationId)
				.containsExactly(1001L, 1002L, 1003L);
			softly.assertThat(seats).extracting(ParticipationHold::getCrewId).containsOnly(CREW_ID);
			softly.assertThat(seats).extracting(ParticipationHold::getExpiresAt).containsOnly(expiresAt);
			softly.assertThat(course.getCrewReserved()).isEqualTo(3);
			softly.assertThat(course.crewRemaining()).isEqualTo(2);
			softly.assertThat(course.getIndividualReserved()).isZero();
		});
	}

	@Test
	@DisplayName("남은 크루 자리 1석인 종목에 3명 파티가 확보하면 held=false·shortages=[{종목, 3, 1}]이고 한 석도 잡지 않는다")
	void notHoldWhenCrewSeatsShort() throws Exception {
		// given
		CrewCourse crewCourse = crewCourseWithQuota(1);

		// when
		ResultActions result = holdCrew(PARTY_ID, crewCourse, tomorrow(), PARTY_MEMBERS);

		// then
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("남은 크루 자리가 모자랍니다."))
			.andExpect(jsonPath("$.data.held").value(false))
			.andExpect(jsonPath("$.data.expiresAt").doesNotExist())
			.andExpect(jsonPath("$.data.shortages.length()").value(1))
			.andExpect(jsonPath("$.data.shortages[0].raceCourseId").value(crewCourse.courseId()))
			.andExpect(jsonPath("$.data.shortages[0].needed").value(3))
			.andExpect(jsonPath("$.data.shortages[0].remaining").value(1));
		assertSoftly(softly -> {
			softly.assertThat(participationHoldRepository.count()).isZero();
			softly.assertThat(courseOf(crewCourse.courseId()).getCrewReserved()).isZero();
		});
	}

	@Test
	@DisplayName("같은 파티로 두 번 확보하면 두 번째는 새로 잡지 않고 처음 결과(held=true, 처음 끝 시각)를 돌려준다. "
		+ "자리는 3석, 크루 결제 대기는 3 그대로다")
	void returnFirstResultForSameParty() throws Exception {
		// given
		CrewCourse crewCourse = crewCourseWithQuota(10);
		LocalDateTime firstExpiresAt = tomorrow();
		holdCrew(PARTY_ID, crewCourse, firstExpiresAt, PARTY_MEMBERS).andExpect(status().isOk());

		// when
		ResultActions again = holdCrew(PARTY_ID, crewCourse, firstExpiresAt.plusHours(1), PARTY_MEMBERS);

		// then
		again.andExpect(jsonPath("$.data.held").value(true));
		LocalDateTime returnedExpiresAt = expiresAtOf(again);
		assertSoftly(softly -> {
			softly.assertThat(returnedExpiresAt).isEqualTo(firstExpiresAt);
			softly.assertThat(participationHoldRepository.count()).isEqualTo(3);
			softly.assertThat(courseOf(crewCourse.courseId()).getCrewReserved()).isEqualTo(3);
		});
	}

	@Test
	@DisplayName("남은 크루 자리 5석에 3명 파티 둘이 동시에 확보하면 한 파티만 잡히고, 크루 결제 대기와 자리 행이 모두 3이다")
	void holdOnlyOnePartyWhenConcurrent() throws Exception {
		// given
		CrewCourse crewCourse = crewCourseWithQuota(5);
		LocalDateTime expiresAt = tomorrow();

		// when
		List<Boolean> held = runConcurrently(2, index -> heldOf(index == 0
			? holdCrew(PARTY_ID, crewCourse, expiresAt, PARTY_MEMBERS)
			: holdCrew(OTHER_PARTY_ID, crewCourse, expiresAt, OTHER_PARTY_MEMBERS)));

		// then
		assertSoftly(softly -> {
			softly.assertThat(held).containsExactlyInAnyOrder(true, false);
			softly.assertThat(courseOf(crewCourse.courseId()).getCrewReserved()).isEqualTo(3);
			softly.assertThat(participationHoldRepository.count()).isEqualTo(3);
		});
	}

	// ---------- 판매·해제·복구 ----------

	@Test
	@DisplayName("크루로 잡은 3석 중 1001 판매, 1002 해제, 1003 판매 뒤 복구를 하면 크루 결제 대기 0·크루 판매 1이고 "
		+ "개인 카운터는 바뀌지 않는다")
	void sellReleaseRestoreChangeCrewCounters() throws Exception {
		// given
		CrewCourse crewCourse = crewCourseWithQuota(5);
		holdCrew(PARTY_ID, crewCourse, tomorrow(), PARTY_MEMBERS).andExpect(status().isOk());

		// when
		sell(1001).andExpect(status().isOk());
		releaseByMessage(1002);
		sell(1003).andExpect(status().isOk());
		restoreByMessage(1003);

		// then
		RaceCourse course = courseOf(crewCourse.courseId());
		assertSoftly(softly -> {
			softly.assertThat(course.getCrewReserved()).isZero();
			softly.assertThat(course.getCrewSold()).isEqualTo(1);
			softly.assertThat(course.crewRemaining()).isEqualTo(4);
			softly.assertThat(course.getIndividualReserved()).isZero();
			softly.assertThat(course.getIndividualSold()).isZero();
			softly.assertThat(course.individualRemaining()).isEqualTo(INDIVIDUAL_QUOTA);
		});
	}

	@Test
	@DisplayName("크루로 판 자리(1001)를 크루 마감 뒤 환불 복구하면, 그 자리는 개인 몫으로 넘어가 남은 개인 자리가 1 늘고 "
		+ "남은 크루 자리는 0 그대로다")
	void restoredCrewSeatGoesToIndividualAfterCrewClosed() throws Exception {
		// given: 크루 5석 중 1001만 결제했고, 크루 마감일이 지난 자정 배치가 결제 대기(1002, 1003)를 반환한 뒤
		// 남은 크루 자리 4석을 개인 몫으로 옮겼다 (배치 코드가 없어 해제 메시지와 행 직접 변경으로 만든다)
		CrewCourse crewCourse = crewCourseWithQuota(5);
		holdCrew(PARTY_ID, crewCourse, tomorrow(), PARTY_MEMBERS).andExpect(status().isOk());
		sell(1001).andExpect(status().isOk());
		releaseByMessage(1002);
		releaseByMessage(1003);
		closeCrewQuota(crewCourse.courseId());

		// when
		restoreByMessage(1001);

		// then
		RaceCourse course = courseOf(crewCourse.courseId());
		assertSoftly(softly -> {
			softly.assertThat(course.getCrewSold()).isZero();
			softly.assertThat(course.getMovedQuota()).isEqualTo(5);
			softly.assertThat(course.crewRemaining()).isZero();
			softly.assertThat(course.individualRemaining()).isEqualTo(INDIVIDUAL_QUOTA + 5);
		});
	}

	// ---------- 도우미 ----------

	// 크루 예매 중인 대회의 종목 (크루 정원 crewQuota, 개인 정원 100)
	private record CrewCourse(long raceId, long courseId) {
	}

	// 지금 크루 예매 기간(1시간 전 ~ 내일)인 판매 중 대회를 등록한다
	// 개인 예매는 크루 예매 마감일의 자정 뒤 06:00에 열린다 (등록 검사: 크루 예매 마감일의 자정 이후)
	private CrewCourse crewCourseWithQuota(int crewQuota) throws Exception {
		RaceBuilder builder = race();
		LocalDateTime now = builder.now();
		LocalDateTime crewSaleEndAt = now.plusDays(1);
		RegisteredRace registered = registerOnSale(builder
			.crewSale(now.minusHours(1), crewSaleEndAt)
			.individualOpenAt(crewSaleEndAt.toLocalDate().plusDays(1).atTime(6, 0))
			.course(course().quota(crewQuota, INDIVIDUAL_QUOTA))
			.build());
		return new CrewCourse(registered.raceId(), registered.courseId());
	}

	// 주문 서비스가 보내는 요청 그대로: 파티원마다 참가 신청 ID와 (모두 같은) 종목
	private ResultActions holdCrew(long partyId, CrewCourse crewCourse, LocalDateTime expiresAt,
		long... participationIds) throws Exception {
		String seats = Arrays.stream(participationIds)
			.mapToObj(id -> "{\"participationId\":%d,\"raceCourseId\":%d}".formatted(id, crewCourse.courseId()))
			.collect(Collectors.joining(","));
		String body = "{\"partyId\":%d,\"crewId\":%d,\"raceId\":%d,\"expiresAt\":\"%s\",\"seats\":[%s]}"
			.formatted(partyId, CREW_ID, crewCourse.raceId(), expiresAt, seats);
		return mockMvc.perform(post(CREW_HOLDS_PATH)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	// 주문 서비스가 product.hold.cancel로 보내는 본문 그대로 리스너 메서드에 넘긴다 (헤더 없이)
	private void releaseByMessage(long participationId) {
		productKafkaConsumer.handleHoldCancel("{\"participationId\": %d}".formatted(participationId), null);
	}

	// 크루 마감일이 지난 자정 배치가 결제 대기를 반환한 뒤 하는 것처럼, 남은 크루 자리를 개인 몫으로 옮긴다
	// (배치 코드가 아직 없어 행을 직접 바꾼다)
	private void closeCrewQuota(long raceCourseId) {
		transactionTemplate.executeWithoutResult(status -> entityManager.createQuery("""
				update RaceCourse c set c.movedQuota = c.crewQuota - c.crewReserved - c.crewSold,
					c.crewClosedAt = :closedAt
				where c.id = :id""")
			.setParameter("closedAt", LocalDateTime.now())
			.setParameter("id", raceCourseId)
			.executeUpdate());
	}

	// 파티원 결제 기한 (DB에 저장했다 읽어도 같게 나노초는 버린다)
	private static LocalDateTime tomorrow() {
		return LocalDateTime.now().plusDays(1).withNano(0);
	}

	private String bodyOf(ResultActions result) throws Exception {
		return result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	private boolean heldOf(ResultActions result) throws Exception {
		return JsonPath.read(bodyOf(result), "$.data.held");
	}

	// 응답의 끝 시각 (초가 0이면 문자열에서 초가 빠지므로 시각으로 바꿔 비교한다)
	private LocalDateTime expiresAtOf(ResultActions result) throws Exception {
		return LocalDateTime.parse(JsonPath.read(bodyOf(result), "$.data.expiresAt"));
	}

	private RaceCourse courseOf(long raceCourseId) {
		return raceCourseRepository.findById(raceCourseId).orElseThrow();
	}
}
