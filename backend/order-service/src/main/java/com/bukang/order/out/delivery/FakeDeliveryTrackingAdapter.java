package com.bukang.order.out.delivery;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.bukang.order.domain.DeliveryCarrier;
import com.bukang.order.domain.DeliveryTrackingStatus;

import lombok.extern.slf4j.Slf4j;

/**
 * 가짜 어댑터: Delivery Tracker 키 없이 개발할 때 쓴다 (BUK-55). 운영(prod)은 http다
 * 가짜 택배사(dev.track.dummy)만 Delivery Tracker의 가짜 운송장처럼 답한다: 운송장 번호가 시각(예: 2026-11-03T12:00:00Z)이고
 * 그 시각이 지났으면 배송 완료, 아니면 이동 중이다. 그 밖의 운송장은 아직 조회되지 않음이다
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "order.clients.delivery-tracker", name = "mode", havingValue = "fake",
	matchIfMissing = true)
public class FakeDeliveryTrackingAdapter implements DeliveryTrackingPort {
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	public FakeDeliveryTrackingAdapter() {
		log.warn("배송 조회는 가짜 응답(FakeDeliveryTrackingAdapter)을 씁니다. 실제 조회는 order.clients.delivery-tracker.mode=http");
	}

	@Override
	public DeliveryTrackingResult track(String carrierId, String trackingNumber) {
		if (!DeliveryCarrier.DUMMY.getId().equals(carrierId)) {
			return DeliveryTrackingResult.notTracked();
		}
		Instant deliveredAt;
		try {
			deliveredAt = Instant.parse(trackingNumber);
		} catch (DateTimeParseException exception) {
			return DeliveryTrackingResult.notTracked();
		}
		LocalDateTime delivered = LocalDateTime.ofInstant(deliveredAt, ZONE);
		DeliveryTrackingResult.Event pickedUp = new DeliveryTrackingResult.Event(delivered.minusDays(1),
			DeliveryTrackingStatus.IN_TRANSIT, "간선상차", "가짜 운송장 이동 중", "가짜 터미널");
		if (Instant.now().isBefore(deliveredAt)) {
			return new DeliveryTrackingResult(DeliveryTrackingStatus.IN_TRANSIT, "간선상차", pickedUp.time(),
				List.of(pickedUp));
		}
		DeliveryTrackingResult.Event done = new DeliveryTrackingResult.Event(delivered,
			DeliveryTrackingStatus.DELIVERED, "배달완료", "가짜 운송장 배달 완료", "가짜 대리점");
		return new DeliveryTrackingResult(DeliveryTrackingStatus.DELIVERED, "배달완료", delivered, List.of(pickedUp, done));
	}
}
