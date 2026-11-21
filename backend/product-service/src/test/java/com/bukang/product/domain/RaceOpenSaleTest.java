package com.bukang.product.domain;

import static com.bukang.product.support.RaceRequests.race;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.product.domain.exception.RaceChangeNotAllowedException;

/**
 * 판매 예정 대회를 바로 열 수 있는지(Race.checkOpenable)를 확인한다 (CRU-44)
 * 판매 예정이고, 당길 판매 시작 일정이 아직 오지 않았고, 접수 마감 전일 때만 열 수 있다
 */
class RaceOpenSaleTest {
	private static final LocalDateTime REGISTERED_AT = LocalDateTime.of(2026, 11, 1, 10, 0);
	private static final LocalDateTime INDIVIDUAL_OPEN_AT = LocalDateTime.of(2026, 11, 10, 10, 0);
	private static final LocalDateTime APPLICATION_CLOSE_AT = LocalDateTime.of(2026, 11, 20, 18, 0);

	@Test
	@DisplayName("판매 예정이고 판매 시작 일정 전이면 바로 열 수 있다")
	void openScheduledRace() {
		Race scheduled = scheduledRace();

		assertThatCode(() -> scheduled.checkOpenable(REGISTERED_AT.plusHours(1))).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("이미 판매 중인 대회는 바로 열 수 없다")
	void rejectOnSaleRace() {
		// given: 당일 판매로 오늘 열리는 대회를 등록하면 처음부터 판매 중이다
		Race onSale = new Race(seller(), race()
			.individualOpenAt(REGISTERED_AT.minusHours(1))
			.applicationCloseAt(APPLICATION_CLOSE_AT)
			.raceDate(LocalDateTime.of(2026, 12, 13, 8, 0))
			.refundDeadline(LocalDate.of(2026, 11, 21))
			.build(), "on-sale-key", REGISTERED_AT, true);

		assertThatThrownBy(() -> onSale.checkOpenable(REGISTERED_AT.plusHours(1)))
			.isInstanceOf(RaceChangeNotAllowedException.class)
			.hasMessage("이미 판매 중인 대회입니다.");
	}

	@Test
	@DisplayName("판매 시작 일정이 이미 지났으면 바로 열지 않는다 (상태 배치가 곧 판매 중으로 바꾼다)")
	void rejectAfterSaleOpenSchedule() {
		Race scheduled = scheduledRace();

		assertThatThrownBy(() -> scheduled.checkOpenable(INDIVIDUAL_OPEN_AT))
			.isInstanceOf(RaceChangeNotAllowedException.class)
			.hasMessage("판매 시작 시각이 이미 지났습니다. 잠시 뒤 판매 중으로 바뀝니다.");
	}

	@Test
	@DisplayName("접수 마감이 지난 대회는 바로 열 수 없다")
	void rejectAfterApplicationClose() {
		Race scheduled = scheduledRace();

		assertThatThrownBy(() -> scheduled.checkOpenable(APPLICATION_CLOSE_AT))
			.isInstanceOf(RaceChangeNotAllowedException.class)
			.hasMessage("접수 마감이 지난 대회는 판매를 시작할 수 없습니다.");
	}

	private static Race scheduledRace() {
		return new Race(seller(), race()
			.individualOpenAt(INDIVIDUAL_OPEN_AT)
			.applicationCloseAt(APPLICATION_CLOSE_AT)
			.raceDate(LocalDateTime.of(2026, 12, 13, 8, 0))
			.refundDeadline(LocalDate.of(2026, 11, 21))
			.build(), "registered-key", REGISTERED_AT, false);
	}

	private static ProductSeller seller() {
		ProductMember member = new ProductMember(9001L, REGISTERED_AT, REGISTERED_AT, "러너9001");
		return new ProductSeller(7001L, member, SellerType.BUSINESS, "(주)러닝7001", "123-45-67890",
			"김대표", SellerStatus.APPROVED, REGISTERED_AT, REGISTERED_AT);
	}
}
