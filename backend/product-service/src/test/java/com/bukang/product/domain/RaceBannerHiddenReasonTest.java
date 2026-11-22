package com.bukang.product.domain;

import static com.bukang.product.support.RaceRequests.race;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.common.shared.seller.domain.SellerType;

/**
 * 배너로 고른 대회가 홈에 보이지 않는 이유(Race.bannerHiddenReason)를 확인한다 (CRU-147)
 * 이유가 여러 개면 되돌릴 수 없는 것부터 하나만 준다: 취소 → 판매 종료 → 판매 중지 → 접수 마감
 */
class RaceBannerHiddenReasonTest {
	private static final LocalDateTime REGISTERED_AT = LocalDateTime.of(2026, 11, 1, 10, 0);
	private static final LocalDateTime APPLICATION_CLOSE_AT = LocalDateTime.of(2026, 11, 20, 18, 0);

	@Test
	@DisplayName("취소되고 접수 마감도 지난 대회는 홈에 보이지 않는 이유로 대회 취소 하나만 준다")
	void canceledComesBeforeApplicationClosed() {
		// given: 취소된 대회, 접수 마감 다음 날
		Race race = registeredRace();
		ReflectionTestUtils.setField(race, "saleStatus", RaceSaleStatus.CANCELED);
		LocalDateTime afterClose = APPLICATION_CLOSE_AT.plusDays(1);

		// when
		BannerHiddenReason reason = race.bannerHiddenReason(afterClose);

		// then
		assertThat(reason).isEqualTo(BannerHiddenReason.CANCELED);
	}

	// 판정의 관심사는 판매 상태와 접수 마감이라 나머지 일정은 등록 검사를 지나는 값으로만 둔다
	private static Race registeredRace() {
		ProductMember member = new ProductMember(9001L, REGISTERED_AT, REGISTERED_AT, "러너9001");
		ProductSeller seller = new ProductSeller(7001L, member, SellerType.BUSINESS, "(주)러닝7001", "123-45-67890",
			"김대표", SellerStatus.APPROVED, REGISTERED_AT, REGISTERED_AT);
		return new Race(seller, race()
			.individualOpenAt(LocalDateTime.of(2026, 11, 10, 10, 0))
			.applicationCloseAt(APPLICATION_CLOSE_AT)
			.raceDate(LocalDateTime.of(2026, 12, 13, 8, 0))
			.refundDeadline(LocalDate.of(2026, 11, 21))
			.build(), "registered-key", REGISTERED_AT, false);
	}
}
