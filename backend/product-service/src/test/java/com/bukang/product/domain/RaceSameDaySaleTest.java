package com.bukang.product.domain;

import static com.bukang.product.support.RaceRequests.race;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.product.domain.exception.InvalidRaceException;
import com.bukang.product.dto.RaceSaveRequestDto;

/**
 * 테스트용 당일 판매 설정(product.race.same-day-sale-enabled)에 따라 등록이 달라지는지 확인한다 (CRU-36)
 * 켜면 판매 시작일이 오늘인 대회도 등록되고 처음부터 판매 중이다. 꺼져 있으면 판매 시작일은 오늘로부터 이틀 뒤부터다
 */
class RaceSameDaySaleTest {
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 10, 0);

	@Test
	@DisplayName("당일 판매를 켜고 판매 시작일을 오늘로 등록하면 처음부터 판매 중이다")
	void registerOnSaleWhenSameDaySaleEnabled() {
		// given: 개인 예매가 1분 전에 열린 대회 (판매 시작일이 오늘)
		RaceSaveRequestDto request = openedTodayRequest();

		// when
		Race race = new Race(seller(), request, "same-day-key", NOW, true);

		// then
		assertThat(race.getSaleStatus()).isEqualTo(RaceSaleStatus.ON_SALE);
	}

	@Test
	@DisplayName("당일 판매가 꺼져 있으면 판매 시작일이 오늘인 등록은 이틀 뒤부터라는 오류로 막힌다")
	void rejectSameDaySaleWhenDisabled() {
		// given: 개인 예매가 1분 전에 열린 대회 (판매 시작일이 오늘)
		RaceSaveRequestDto request = openedTodayRequest();

		// when, then
		assertThatThrownBy(() -> new Race(seller(), request, "same-day-key", NOW, false))
			.isInstanceOf(InvalidRaceException.class)
			.hasMessage("판매 시작일은 오늘로부터 이틀 뒤(11월 20일)부터 정할 수 있습니다. "
				+ "크루 예매 시작·개인 예매 오픈 일시를 확인해주세요.");
	}

	// 판매 시작일(개인 예매 오픈 날짜)이 오늘인 요청. 관심사는 개인 예매 오픈뿐이라 나머지 일정은 그 뒤로 둔다
	private static RaceSaveRequestDto openedTodayRequest() {
		return race()
			.individualOpenAt(NOW.minusMinutes(1))
			.applicationCloseAt(LocalDateTime.of(2026, 11, 28, 18, 0))
			.raceDate(LocalDateTime.of(2026, 12, 20, 8, 0))
			.refundDeadline(LocalDate.of(2026, 11, 29))
			.build();
	}

	private static ProductSeller seller() {
		ProductMember member = new ProductMember(9001L, NOW, NOW, "러너9001");
		return new ProductSeller(7001L, member, SellerType.BUSINESS, "(주)러닝7001", "123-45-67890",
			"김대표", SellerStatus.APPROVED, NOW, NOW);
	}
}
