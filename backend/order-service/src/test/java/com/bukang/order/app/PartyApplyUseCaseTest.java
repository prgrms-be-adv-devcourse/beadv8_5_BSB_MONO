package com.bukang.order.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.order.out.race.RaceCourseInfo;
import com.bukang.order.out.race.RaceCourseInfo.CrewSale;
import com.bukang.order.support.StubRaceCoursePort;

class PartyApplyUseCaseTest {
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 12, 0);

	@Test
	@DisplayName("결제 기한은 신청 뒤 24시간, 크루 마감, 접수 마감 중 가장 이른 때다 (11/05 회의 P2)")
	void paymentDueAt() {
		assertThat(dueAt(NOW.plusDays(5), NOW.plusDays(9))).isEqualTo(NOW.plusHours(24));
		assertThat(dueAt(NOW.plusHours(3), NOW.plusDays(9))).isEqualTo(NOW.plusHours(3));
		assertThat(dueAt(NOW.plusHours(3), NOW.plusHours(2))).isEqualTo(NOW.plusHours(2));
	}

	private static LocalDateTime dueAt(LocalDateTime crewSaleEndAt, LocalDateTime applicationCloseAt) {
		RaceCourseInfo open = StubRaceCoursePort.openCourse(1, 1, 14, null);
		RaceCourseInfo course = new RaceCourseInfo(1, 1, open.raceDate(), NOW.minusDays(1), applicationCloseAt,
			14, null, 1, "종목", 1, false, open.refundDeadline(), null);
		CrewSale crewSale = new CrewSale(NOW.minusDays(1), crewSaleEndAt, 4, 10);
		return PartyApplyUseCase.paymentDueAt(course, crewSale, NOW);
	}
}
