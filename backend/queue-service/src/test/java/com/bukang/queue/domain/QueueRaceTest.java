package com.bukang.queue.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.common.shared.race.domain.RaceSaleStatus;

/**
 * 대기열 진입 판정의 시각 경계를 지금 시각을 직접 넣어 확인한다.
 * 개인 줄(isEnterable)은 개인 예매 오픈 시각은 포함하고(이상), 접수 마감 시각은 포함하지 않는다(미만).
 * 파티 줄(isPartyEnterable)은 크루 예매 시작 시각부터 들어올 수 있다.
 */
class QueueRaceTest {
	private static final LocalDateTime INDIVIDUAL_OPEN_AT = LocalDateTime.of(2026, 10, 20, 10, 0);
	private static final LocalDateTime APPLICATION_CLOSE_AT = LocalDateTime.of(2026, 11, 1, 18, 0);
	// 크루 예매는 10/16 07:00에 열린다 (판매 시작일 0시가 아니라 그날 아침)
	private static final LocalDateTime CREW_SALE_START_AT = LocalDateTime.of(2026, 10, 16, 7, 0);
	private static final LocalDateTime CREW_SALE_END_AT = LocalDateTime.of(2026, 10, 18, 22, 0);

	private final QueueRace race = new QueueRace(1, RaceSaleStatus.ON_SALE, INDIVIDUAL_OPEN_AT, APPLICATION_CLOSE_AT,
		null, null, LocalDateTime.of(2026, 10, 15, 10, 0));

	@Test
	@DisplayName("isEnterable: 개인 예매 오픈 시각 정각부터 들어올 수 있다 (1나노초 전은 안 된다)")
	void enterableFromIndividualOpenAt() {
		assertThat(race.isEnterable(INDIVIDUAL_OPEN_AT)).isTrue();
		assertThat(race.isEnterable(INDIVIDUAL_OPEN_AT.minusNanos(1))).isFalse();
	}

	@Test
	@DisplayName("isEnterable: 접수 마감 시각 정각부터는 들어올 수 없다 (1나노초 전까지는 된다)")
	void notEnterableFromApplicationCloseAt() {
		assertThat(race.isEnterable(APPLICATION_CLOSE_AT)).isFalse();
		assertThat(race.isEnterable(APPLICATION_CLOSE_AT.minusNanos(1))).isTrue();
	}

	@Test
	@DisplayName("isPartyEnterable: 크루 예매가 07:00에 열리는 날 00:00에는 판매 중이어도 파티 대기열에 들어올 수 없다")
	void notPartyEnterableAtMidnightBeforeCrewSaleStart() {
		// given
		QueueRace crewRace = crewRace();
		LocalDateTime midnight = CREW_SALE_START_AT.toLocalDate().atStartOfDay();

		// when
		boolean enterable = crewRace.isPartyEnterable(midnight);

		// then
		assertThat(enterable).isFalse();
	}

	@Test
	@DisplayName("isPartyEnterable: 크루 예매 시작 시각(07:00) 정각에는 파티 대기열에 들어올 수 있다")
	void partyEnterableAtCrewSaleStart() {
		// given
		QueueRace crewRace = crewRace();

		// when
		boolean enterable = crewRace.isPartyEnterable(CREW_SALE_START_AT);

		// then
		assertThat(enterable).isTrue();
	}

	// 판매 중이고 크루 예매(10/16 07:00 ~ 10/18 22:00)가 있는 대회. 판정의 관심사는 시각뿐이라 나머지는 기본값이다
	private static QueueRace crewRace() {
		return new QueueRace(1, RaceSaleStatus.ON_SALE, INDIVIDUAL_OPEN_AT, APPLICATION_CLOSE_AT,
			CREW_SALE_START_AT, CREW_SALE_END_AT, LocalDateTime.of(2026, 10, 15, 10, 0));
	}
}
