package com.bukang.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShipmentTrackingEventTest {
	@Test
	@DisplayName("상대가 보낸 긴 글은 글자 단위로 잘라 이모지를 반쪽 남기지 않는다")
	void cutByCodePoint() {
		assertThat(ShipmentTrackingEvent.cut("가나다", 5)).isEqualTo("가나다");
		assertThat(ShipmentTrackingEvent.cut(null, 5)).isNull();
		assertThat(ShipmentTrackingEvent.cut("ab😀cd", 3)).isEqualTo("ab😀");
		assertThat(ShipmentTrackingEvent.cut("😀😀😀", 2)).isEqualTo("😀😀");
	}
}
