package com.bukang.inquiry.out.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderSummaryTest {
	private final OrderSummary order = new OrderSummary(7L, "OD-1", 1_000L, "PAID",
		List.of(new OrderItemSummary(10L, "대회", "10K", null, null)));

	@Test
	@DisplayName("내 주문이고 그 대회가 들어 있을 때만 true다")
	void ownedByWithRace() {
		assertThat(order.isOwnedBy(7L, 10L)).isTrue();
		assertThat(order.isOwnedBy(8L, 10L)).isFalse();
		assertThat(order.isOwnedBy(7L, 11L)).isFalse();
	}
}
