package com.bukang.order.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.order.domain.Gender;
import com.bukang.order.domain.Order;
import com.bukang.order.domain.ParticipantInfo;
import com.bukang.order.domain.Participation;
import com.bukang.order.domain.ShippingAddress;
import com.bukang.order.domain.exception.OrderNotPayableException;
import com.bukang.order.out.race.RaceCourseInfo;
import com.bukang.order.out.race.RaceCourseInfo.CrewSale;
import com.bukang.order.support.StubRaceCoursePort;

class PartyApplyUseCaseTest {
	// 크루 예매 마감 11/12 19:00, 접수 마감은 넉넉히 뒤
	private static final LocalDateTime CREW_END = LocalDateTime.of(2026, 11, 12, 19, 0);
	private static final LocalDateTime APPLICATION_CLOSE = LocalDateTime.of(2026, 11, 30, 18, 0);

	@Test
	@DisplayName("결제 기한은 신청 뒤 24시간과 크루 마감일 다음 날 0시 중 이른 때다 (11/09 호빈님 요청, 11/05 회의 P2)")
	void paymentDueAt() {
		// 마감 전에 24시간이 끝난다
		assertThat(dueAt(LocalDateTime.of(2026, 11, 10, 13, 12), CREW_END, APPLICATION_CLOSE))
			.isEqualTo(LocalDateTime.of(2026, 11, 11, 13, 12));
		// 24시간 뒤가 크루 마감 시각(19:00)을 넘어도 그날 자정 전이면 24시간 그대로다
		assertThat(dueAt(LocalDateTime.of(2026, 11, 11, 22, 0), CREW_END, APPLICATION_CLOSE))
			.isEqualTo(LocalDateTime.of(2026, 11, 12, 22, 0));
		// 24시간 뒤가 마감일 자정을 넘으면 자정에서 자른다
		assertThat(dueAt(LocalDateTime.of(2026, 11, 12, 15, 0), CREW_END, APPLICATION_CLOSE))
			.isEqualTo(LocalDateTime.of(2026, 11, 13, 0, 0));
		assertThat(dueAt(LocalDateTime.of(2026, 11, 12, 18, 59), CREW_END, APPLICATION_CLOSE))
			.isEqualTo(LocalDateTime.of(2026, 11, 13, 0, 0));
	}

	@Test
	@DisplayName("접수 마감이 더 이르면 접수 마감에서 자른다")
	void cappedByApplicationClose() {
		assertThat(dueAt(LocalDateTime.of(2026, 11, 12, 15, 0), CREW_END, LocalDateTime.of(2026, 11, 12, 20, 0)))
			.isEqualTo(LocalDateTime.of(2026, 11, 12, 20, 0));
	}

	@Test
	@DisplayName("크루 마감 19:00 정각부터는 파티 예매 기간이 아니다 (확정 자체가 409)")
	void crewSaleClosedAtEnd() {
		CrewSale crewSale = new CrewSale(CREW_END.minusDays(5), CREW_END, 4, 10);
		assertThat(crewSale.isOpen(CREW_END.minusMinutes(1))).isTrue();
		assertThat(crewSale.isOpen(CREW_END)).isFalse();
	}

	@Test
	@DisplayName("크루 마감일 자정이 기한이면 23:59:59까지 결제할 수 있고 0시 정각부터는 기한이 지났다")
	void payBeforeMidnight() {
		LocalDateTime midnight = LocalDateTime.of(2026, 11, 13, 0, 0);
		RaceCourseInfo course = StubRaceCoursePort.openCourse(1, 1, 14, null);
		ParticipantInfo info = new ParticipantInfo("김러너", LocalDate.of(1994, 6, 25), Gender.MALE, "010-1234-5678",
			"김가족", "010-9876-5432", "L",
			new ShippingAddress("김러너", "010-1234-5678", "04700", "서울 성동구 왕십리로 00", null));
		Order order = Order.forParticipation(Participation.individual(1, 1, 1, info, midnight.minusDays(1)), course,
			"key", midnight);

		assertThatCode(() -> order.checkPayable(midnight.minusSeconds(1))).doesNotThrowAnyException();
		assertThatThrownBy(() -> order.checkPayable(midnight)).isInstanceOf(OrderNotPayableException.class);
	}

	private static LocalDateTime dueAt(LocalDateTime now, LocalDateTime crewSaleEndAt,
		LocalDateTime applicationCloseAt) {
		RaceCourseInfo open = StubRaceCoursePort.openCourse(1, 1, 14, null);
		RaceCourseInfo course = new RaceCourseInfo(1, 1, open.raceDate(), now.minusDays(1), applicationCloseAt,
			14, null, 1, "종목", 1, false, open.refundDeadline(), null);
		CrewSale crewSale = new CrewSale(now.minusDays(1), crewSaleEndAt, 4, 10);
		return PartyApplyUseCase.paymentDueAt(course, crewSale, now);
	}
}
