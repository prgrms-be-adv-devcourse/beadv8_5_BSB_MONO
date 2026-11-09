package com.bukang.product.domain;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 크루 예매 마감일의 자정(Race.crewCloseAt)을 확인한다.
 * 결제 대기 크루 자리를 반환하고 크루 몫을 개인 몫으로 옮기는 시각이고, 개인 예매는 이 시각부터 열 수 있다.
 */
class RaceCrewCloseAtTest {
	@Test
	@DisplayName("크루 예매 마감이 11/12 19:00이면 11/13 00:00이고, 마감이 11/13 00:00 정각이면 그 시각 그대로다")
	void crewCloseAtIsMidnightOfCrewSaleEndDate() {
		// given
		LocalDateTime eveningEnd = LocalDateTime.of(2026, 11, 12, 19, 0);
		LocalDateTime midnightEnd = LocalDateTime.of(2026, 11, 13, 0, 0);

		// when
		LocalDateTime afterEvening = Race.crewCloseAt(eveningEnd);
		LocalDateTime afterMidnight = Race.crewCloseAt(midnightEnd);

		// then
		assertSoftly(softly -> {
			softly.assertThat(afterEvening).isEqualTo(LocalDateTime.of(2026, 11, 13, 0, 0));
			softly.assertThat(afterMidnight).isEqualTo(midnightEnd);
		});
	}
}
