package com.bukang.queue.domain;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.common.shared.race.domain.RaceSaleStatus;

/**
 * 오늘 날짜로 들어갈 대기열을 정하는 판정(QueueRace.entryTypeOn·openDate·closeDate)을 날짜를 직접 넣어 확인한다.
 * 크루 마지막 날까지는 파티, 그 뒤는 개인이다. 크루 예매 마감이 00:00 정각이면 그 전날이 크루 마지막 날이다.
 */
class QueueRaceEntryTest {
	// 개인 예매는 크루 예매 마감일의 자정(11/13 00:00)부터, 접수 마감은 11/20 18:00
	private static final LocalDateTime INDIVIDUAL_OPEN_AT = LocalDateTime.of(2026, 11, 13, 0, 0);
	private static final LocalDateTime APPLICATION_CLOSE_AT = LocalDateTime.of(2026, 11, 20, 18, 0);
	private static final LocalDateTime CREW_SALE_START_AT = LocalDateTime.of(2026, 11, 10, 7, 0);

	@Test
	@DisplayName("크루 예매가 11/12 19:00에 마감되는 대회는 11/12에 파티 대기열이고, 기간은 11/10 ~ 11/12다")
	void partyOnCrewLastDate() {
		// given
		QueueRace race = crewRace(LocalDateTime.of(2026, 11, 12, 19, 0));

		// when
		QueueEntryType entryType = race.entryTypeOn(LocalDate.of(2026, 11, 12));

		// then
		assertSoftly(softly -> {
			softly.assertThat(entryType).isEqualTo(QueueEntryType.PARTY);
			softly.assertThat(race.openDate(entryType)).isEqualTo(LocalDate.of(2026, 11, 10));
			softly.assertThat(race.closeDate(entryType)).isEqualTo(LocalDate.of(2026, 11, 12));
		});
	}

	@Test
	@DisplayName("크루 예매가 11/12 19:00에 마감되는 대회는 다음 날 11/13에 개인 대기열이고, 기간은 11/13 ~ 11/20이다")
	void individualAfterCrewLastDate() {
		// given
		QueueRace race = crewRace(LocalDateTime.of(2026, 11, 12, 19, 0));

		// when
		QueueEntryType entryType = race.entryTypeOn(LocalDate.of(2026, 11, 13));

		// then
		assertSoftly(softly -> {
			softly.assertThat(entryType).isEqualTo(QueueEntryType.INDIVIDUAL);
			softly.assertThat(race.openDate(entryType)).isEqualTo(LocalDate.of(2026, 11, 13));
			softly.assertThat(race.closeDate(entryType)).isEqualTo(LocalDate.of(2026, 11, 20));
		});
	}

	@Test
	@DisplayName("크루 예매가 11/13 00:00 정각에 마감되는 대회는 크루 마지막 날이 11/12라, 11/12는 파티이고 11/13은 개인 대기열이다")
	void crewEndAtMidnightMakesPreviousDayLast() {
		// given
		QueueRace race = crewRace(LocalDateTime.of(2026, 11, 13, 0, 0));

		// when
		QueueEntryType onNov12 = race.entryTypeOn(LocalDate.of(2026, 11, 12));
		QueueEntryType onNov13 = race.entryTypeOn(LocalDate.of(2026, 11, 13));

		// then
		assertSoftly(softly -> {
			softly.assertThat(onNov12).isEqualTo(QueueEntryType.PARTY);
			softly.assertThat(race.closeDate(onNov12)).isEqualTo(LocalDate.of(2026, 11, 12));
			softly.assertThat(onNov13).isEqualTo(QueueEntryType.INDIVIDUAL);
		});
	}

	// 판매 중인 크루 예매 대회. 판정의 관심사는 크루 예매 마감 일시라 그것만 받는다
	private static QueueRace crewRace(LocalDateTime crewSaleEndAt) {
		return new QueueRace(1, RaceSaleStatus.ON_SALE, INDIVIDUAL_OPEN_AT, APPLICATION_CLOSE_AT,
			CREW_SALE_START_AT, crewSaleEndAt, LocalDateTime.of(2026, 11, 1, 10, 0));
	}
}
