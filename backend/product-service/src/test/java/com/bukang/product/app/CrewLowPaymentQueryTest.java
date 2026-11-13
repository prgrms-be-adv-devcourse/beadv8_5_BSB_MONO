package com.bukang.product.app;

import static com.bukang.product.domain.HoldStatus.HELD;
import static com.bukang.product.domain.HoldStatus.RELEASED;
import static com.bukang.product.domain.HoldStatus.RESTORED;
import static com.bukang.product.domain.HoldStatus.SOLD;
import static com.bukang.product.support.RaceRequests.race;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.bukang.product.domain.HoldStatus;
import com.bukang.product.dto.CrewLowPaymentDto;
import com.bukang.product.support.ProductIntegrationTest;

/**
 * 결제율 미달 크루 조회(ProductFacade.findLowPaymentCrews)를 확인한다 (BUK-176)
 * 파티 자리는 확보·판매·해제 흐름을 거치지 않고 표에 바로 넣는다. 확보 일시(held_at)를 2026-11로 고정하려고 SQL로 넣는다
 * 파티원 1명 = 자리 1석이고, 자리 상태 목록이 곧 파티 구성이다
 */
class CrewLowPaymentQueryTest extends ProductIntegrationTest {
	private static final YearMonth MONTH = YearMonth.of(2026, 11);
	private static final LocalDateTime HELD_AT = LocalDateTime.of(2026, 11, 10, 12, 0);
	private static final long CREW_ID = 7;
	private static final long OTHER_CREW_ID = 8;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private long courseId;
	// 자리마다 다른 참가 신청 ID (participation_id 유니크)
	private long nextParticipationId = 1;

	@BeforeEach
	void setUp() throws Exception {
		courseId = registerOnSale(race().build()).courseId();
	}

	@Test
	@DisplayName("한 달에 결제율 미달 파티가 3개인 크루만 나오고, 미달이 2개인 크루는 나오지 않는다")
	void onlyCrewWithThreeLowParties() {
		// given: 크루 7은 4명 중 1명 결제한 파티 3개, 크루 8은 그런 파티 2개와 4명 중 2명 결제한 파티 1개
		saveParty(courseId, CREW_ID, 101, SOLD, RELEASED, RELEASED, RELEASED);
		saveParty(courseId, CREW_ID, 102, SOLD, RELEASED, RELEASED, RELEASED);
		saveParty(courseId, CREW_ID, 103, SOLD, RELEASED, RELEASED, RELEASED);
		saveParty(courseId, OTHER_CREW_ID, 201, SOLD, RELEASED, RELEASED, RELEASED);
		saveParty(courseId, OTHER_CREW_ID, 202, SOLD, RELEASED, RELEASED, RELEASED);
		saveParty(courseId, OTHER_CREW_ID, 203, SOLD, SOLD, RELEASED, RELEASED);

		// when
		List<CrewLowPaymentDto> crews = productFacade.findLowPaymentCrews(MONTH);

		// then
		assertThat(crews).containsExactly(new CrewLowPaymentDto(CREW_ID, 3, List.of(
			new CrewLowPaymentDto.Party(101, 4, 1),
			new CrewLowPaymentDto.Party(102, 4, 1),
			new CrewLowPaymentDto.Party(103, 4, 1))));
	}

	@Test
	@DisplayName("결제 대기 자리가 남은 파티와 취소된 대회의 파티는 미달로 세지 않는다")
	void skipHeldAndCanceledRaceParties() throws Exception {
		// given: 미달로 끝난 파티 3개, 결제 대기가 남은 파티 1개, 취소된 대회의 파티 1개 (모두 결제 1명 이하)
		long canceledCourseId = registerOnSale(race().build()).courseId();
		cancelRaceOf(canceledCourseId);
		saveParty(courseId, CREW_ID, 101, SOLD, RELEASED, RELEASED, RELEASED);
		saveParty(courseId, CREW_ID, 102, SOLD, RELEASED, RELEASED, RELEASED);
		saveParty(courseId, CREW_ID, 103, SOLD, RELEASED, RELEASED, RELEASED);
		saveParty(courseId, CREW_ID, 104, HELD, RELEASED, RELEASED, RELEASED);
		saveParty(canceledCourseId, CREW_ID, 105, RESTORED, RELEASED, RELEASED, RELEASED);

		// when
		List<CrewLowPaymentDto> crews = productFacade.findLowPaymentCrews(MONTH);

		// then
		assertThat(crews).singleElement()
			.extracting(crew -> crew.lowPaymentParties().stream().map(CrewLowPaymentDto.Party::partyId).toList())
			.isEqualTo(List.of(101L, 102L, 103L));
	}

	@Test
	@DisplayName("결제한 뒤 환불해 자리가 돌아간 파티원은 결제하지 않은 것으로 세어, 4명 중 2명 결제·1명 환불인 파티도 미달이다")
	void restoredSeatCountsAsUnpaid() {
		// given: 4명 중 2명이 결제했지만 그중 1명이 환불한 파티 3개 (환불을 결제로 치면 50%라 미달이 아니다)
		saveParty(courseId, CREW_ID, 101, SOLD, RESTORED, RELEASED, RELEASED);
		saveParty(courseId, CREW_ID, 102, SOLD, RESTORED, RELEASED, RELEASED);
		saveParty(courseId, CREW_ID, 103, SOLD, RESTORED, RELEASED, RELEASED);

		// when
		List<CrewLowPaymentDto> crews = productFacade.findLowPaymentCrews(MONTH);

		// then
		assertThat(crews).singleElement()
			.extracting(CrewLowPaymentDto::lowPaymentCount)
			.isEqualTo(3);
	}

	// 파티 하나의 크루 자리를 상태별로 넣는다. 한 파티의 자리는 한꺼번에 잡히므로 확보 일시가 같다
	private void saveParty(long raceCourseId, long crewId, long partyId, HoldStatus... statuses) {
		for (HoldStatus status : statuses) {
			jdbcTemplate.update("""
				insert into participation_hold
					(race_course_id, participation_id, party_id, crew_id, status, expires_at, held_at, updated_at)
				values (?, ?, ?, ?, ?, ?, ?, ?)
				""", raceCourseId, nextParticipationId++, partyId, crewId, status.name(),
				HELD_AT.plusHours(24), HELD_AT, HELD_AT);
		}
	}

	// 판매자가 대회를 취소한 상태로 만든다 (취소 API의 다른 처리는 이 테스트의 관심이 아니다)
	private void cancelRaceOf(long raceCourseId) {
		jdbcTemplate.update("""
			update race set canceled_at = ?
			where race_id = (select race_id from race_course where race_course_id = ?)
			""", HELD_AT.plusDays(1), raceCourseId);
	}
}
