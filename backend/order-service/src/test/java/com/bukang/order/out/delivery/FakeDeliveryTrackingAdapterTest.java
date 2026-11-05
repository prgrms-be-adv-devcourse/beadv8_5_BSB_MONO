package com.bukang.order.out.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.order.domain.DeliveryTrackingStatus;

class FakeDeliveryTrackingAdapterTest {
	private final FakeDeliveryTrackingAdapter adapter = new FakeDeliveryTrackingAdapter();

	@Test
	@DisplayName("가짜 택배사 운송장은 번호의 시각 전에는 이동 중, 지나면 배송 완료다. 그 밖은 아직 조회되지 않음이다")
	void dummyCarrier() {
		String future = Instant.now().plus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS).toString();
		String past = Instant.now().minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS).toString();

		assertThat(adapter.track("dev.track.dummy", future).status()).isEqualTo(DeliveryTrackingStatus.IN_TRANSIT);
		DeliveryTrackingResult delivered = adapter.track("dev.track.dummy", past);
		assertThat(delivered.status()).isEqualTo(DeliveryTrackingStatus.DELIVERED);
		assertThat(delivered.events()).hasSize(2);
		assertThat(adapter.track("dev.track.dummy", "test").isTracked()).isFalse();
		assertThat(adapter.track("kr.cjlogistics", past).isTracked()).isFalse();
	}
}
