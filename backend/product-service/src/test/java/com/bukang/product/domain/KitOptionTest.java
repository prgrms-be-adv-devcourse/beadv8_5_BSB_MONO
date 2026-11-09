package com.bukang.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.product.domain.exception.RaceChangeNotAllowedException;

/**
 * 키트 옵션 수정 규칙(KitOption.update, checkRemovable)을 확인한다.
 * 판매가 시작된 뒤에는 이미 옵션을 고른 구매자가 있어, 준비 수량 늘리기만 된다 (11/08 결정, docs/policy/questions.md).
 */
class KitOptionTest {
	private static final boolean SALE_STARTED = true;
	private static final boolean BEFORE_SALE = false;

	@Test
	@DisplayName("판매가 시작된 뒤 준비 수량을 100에서 150으로 늘리면 150이 된다")
	void increaseStockAfterSaleStarted() {
		// given
		KitOption option = option(100);

		// when
		option.update("T-shirt", "L", 150, SALE_STARTED);

		// then
		assertThat(option.getStock()).isEqualTo(150);
	}

	@Test
	@DisplayName("판매가 시작된 뒤 준비 수량을 100에서 80으로 줄이면 409이고 100 그대로다")
	void rejectStockDecreaseAfterSaleStarted() {
		// given
		KitOption option = option(100);

		// when, then
		assertSoftly(softly -> {
			softly.assertThatThrownBy(() -> option.update("T-shirt", "L", 80, SALE_STARTED))
				.isInstanceOf(RaceChangeNotAllowedException.class)
				.hasMessage("판매가 시작된 대회는 키트 옵션 준비 수량을 줄일 수 없습니다. (옵션: T-shirt L)");
			softly.assertThat(option.getStock()).isEqualTo(100);
		});
	}

	@Test
	@DisplayName("판매가 시작된 뒤에는 맞춤 제작을 준비 수량 100으로, 준비 수량 100을 맞춤 제작으로 바꾸면 둘 다 409다")
	void rejectSwitchBetweenCustomMadeAndStockAfterSaleStarted() {
		// given
		KitOption customMade = option(null);
		KitOption stocked = option(100);
		String message = "판매가 시작된 대회는 키트 옵션을 맞춤 제작과 준비 수량 사이에서 바꿀 수 없습니다. (옵션: T-shirt L)";

		// when, then
		assertSoftly(softly -> {
			softly.assertThatThrownBy(() -> customMade.update("T-shirt", "L", 100, SALE_STARTED))
				.isInstanceOf(RaceChangeNotAllowedException.class)
				.hasMessage(message);
			softly.assertThatThrownBy(() -> stocked.update("T-shirt", "L", null, SALE_STARTED))
				.isInstanceOf(RaceChangeNotAllowedException.class)
				.hasMessage(message);
		});
	}

	@Test
	@DisplayName("판매가 시작되기 전에는 준비 수량 100을 맞춤 제작(null)으로 바꿀 수 있다")
	void switchToCustomMadeBeforeSale() {
		// given
		KitOption option = option(100);

		// when
		option.update("T-shirt", "L", null, BEFORE_SALE);

		// then
		assertThat(option.isCustomMade()).isTrue();
	}

	@Test
	@DisplayName("판매가 시작된 뒤 옵션을 빼려 하면 409다")
	void rejectRemoveAfterSaleStarted() {
		// given
		KitOption option = option(100);

		// when, then
		assertThatThrownBy(() -> option.checkRemovable(SALE_STARTED))
			.isInstanceOf(RaceChangeNotAllowedException.class)
			.hasMessage("판매가 시작된 대회는 키트 옵션을 빼거나 이름·값을 바꿀 수 없습니다. (옵션: T-shirt L)");
	}

	// T-shirt L 옵션. 수정 규칙은 대회를 보지 않으므로 대회는 넣지 않는다
	private static KitOption option(Integer stock) {
		return new KitOption(null, "T-shirt", "L", stock);
	}
}
