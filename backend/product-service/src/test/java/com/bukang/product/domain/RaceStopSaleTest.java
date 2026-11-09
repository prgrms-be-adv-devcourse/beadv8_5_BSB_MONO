package com.bukang.product.domain;

import static com.bukang.product.support.RaceRequests.race;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.product.domain.exception.RaceChangeNotAllowedException;

/**
 * 판매를 멈출 수 있는지(Race.checkStoppable)를 확인한다. 판매 중인 대회만 멈출 수 있다 (11/08 결정, docs/policy/questions.md)
 */
class RaceStopSaleTest {
	private static final LocalDateTime REGISTERED_AT = LocalDateTime.of(2026, 11, 1, 10, 0);

	@Test
	@DisplayName("판매 시작 전(판매 예정)인 대회는 판매를 멈출 수 없고, 대회 수정으로 일정을 바꾸라고 알린다")
	void rejectStopBeforeSale() {
		// given: 새로 등록한 대회는 판매 예정이다
		Race scheduled = scheduledRace();

		// when, then
		assertThatThrownBy(() -> scheduled.checkStoppable(REGISTERED_AT.plusHours(1)))
			.isInstanceOf(RaceChangeNotAllowedException.class)
			.hasMessage("판매 중인 대회만 판매를 멈출 수 있습니다. 판매 시작 전에는 대회 수정으로 일정을 바꿔 주세요.");
	}

	// 판매 예정 대회. 판정의 관심사는 판매 상태라 일정은 등록 검사를 지나는 값으로만 둔다
	private static Race scheduledRace() {
		ProductMember member = new ProductMember(9001L, REGISTERED_AT, REGISTERED_AT, "러너9001");
		ProductSeller seller = new ProductSeller(7001L, member, SellerType.BUSINESS, "(주)러닝7001", "123-45-67890",
			"김대표", SellerStatus.APPROVED, REGISTERED_AT, REGISTERED_AT);
		return new Race(seller, race()
			.individualOpenAt(LocalDateTime.of(2026, 11, 10, 10, 0))
			.applicationCloseAt(LocalDateTime.of(2026, 11, 20, 18, 0))
			.raceDate(LocalDateTime.of(2026, 11, 25, 8, 0))
			.refundDeadline(LocalDate.of(2026, 11, 20))
			.build(), "registered-key", REGISTERED_AT);
	}
}
