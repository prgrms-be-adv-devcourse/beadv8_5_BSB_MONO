package com.bukang.product.domain;

import static com.bukang.product.support.RaceRequests.race;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.common.shared.seller.domain.SellerType;

/**
 * 크루 자리를 확보할 수 있는 시각(Race.isAcceptingCrew)을 지금 시각을 직접 넣어 확인한다.
 * 크루 예매 시작 시각은 포함하고(이상), 크루 예매 마감 시각은 포함하지 않는다(미만).
 */
class RaceCrewAcceptingTest {
	// 크루 예매는 11/10 07:00에 열린다 (그날 0시가 아니라 아침)
	private static final LocalDateTime CREW_SALE_START_AT = LocalDateTime.of(2026, 11, 10, 7, 0);
	private static final LocalDateTime CREW_SALE_END_AT = LocalDateTime.of(2026, 11, 12, 18, 0);
	// 대회를 등록한 시각 (판매 시작일 이틀 전보다 이르면 된다)
	private static final LocalDateTime REGISTERED_AT = LocalDateTime.of(2026, 11, 1, 10, 0);

	@Test
	@DisplayName("크루 예매가 07:00에 열리는 대회는 06:59에는 크루 자리를 확보할 수 없고, 07:00 정각부터 확보할 수 있다")
	void acceptCrewFromCrewSaleStartAt() {
		// given
		Race race = crewRace();

		// when
		boolean beforeStart = race.isAcceptingCrew(CREW_SALE_START_AT.minusMinutes(1));
		boolean atStart = race.isAcceptingCrew(CREW_SALE_START_AT);

		// then
		assertSoftly(softly -> {
			softly.assertThat(beforeStart).isFalse();
			softly.assertThat(atStart).isTrue();
		});
	}

	// 판매 예정인 크루 예매 대회. 판정의 관심사는 크루 예매 일정뿐이라 나머지 일정은 그 뒤로 둔다
	// (개인 예매는 크루 마감 다음 날 06:00, 접수 마감·대회일은 그 뒤)
	private static Race crewRace() {
		ProductMember member = new ProductMember(9001L, REGISTERED_AT, REGISTERED_AT, "러너9001");
		ProductSeller seller = new ProductSeller(7001L, member, SellerType.BUSINESS, "(주)러닝7001", "123-45-67890",
			"김대표", SellerStatus.APPROVED, REGISTERED_AT, REGISTERED_AT);
		return new Race(seller, race()
			.crewSale(CREW_SALE_START_AT, CREW_SALE_END_AT)
			.individualOpenAt(LocalDateTime.of(2026, 11, 13, 6, 0))
			.applicationCloseAt(LocalDateTime.of(2026, 11, 20, 18, 0))
			.raceDate(LocalDateTime.of(2026, 11, 25, 8, 0))
			.refundDeadline(LocalDate.of(2026, 11, 20))
			.build(), "registered-key", REGISTERED_AT);
	}
}
