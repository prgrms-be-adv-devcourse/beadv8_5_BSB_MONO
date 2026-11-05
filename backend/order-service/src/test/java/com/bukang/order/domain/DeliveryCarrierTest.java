package com.bukang.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeliveryCarrierTest {
	@Test
	@DisplayName("목록의 택배사 ID만 고를 수 있고, 가짜 택배사는 개발 설정에서만 고를 수 있다")
	void find() {
		assertThat(DeliveryCarrier.find("kr.cjlogistics", false)).contains(DeliveryCarrier.CJ_LOGISTICS);
		assertThat(DeliveryCarrier.find("CJ대한통운", true)).isEmpty();
		assertThat(DeliveryCarrier.find(null, true)).isEmpty();
		assertThat(DeliveryCarrier.find("dev.track.dummy", false)).isEmpty();
		assertThat(DeliveryCarrier.find("dev.track.dummy", true)).contains(DeliveryCarrier.DUMMY);
	}
}
