package com.bukang.order.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.bukang.order.domain.DeliveryTrackingStatus;
import com.bukang.order.domain.ShipmentTrackingEvent;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 키트 배송 조회 칸 (BUK-55). 조회 배치가 마지막으로 저장한 값이라 Delivery Tracker가 멈춰도 그대로 보인다
 * 발송 직후에는 status가 NOT_TRACKED(아직 조회되지 않음)이고 기록이 비어 있다
 */
@Schema(description = "키트 배송 조회 (Delivery Tracker에서 받아 저장한 값)")
public record KitTrackingDto(
	@Schema(description = "택배사 ID (Delivery Tracker)", example = "kr.cjlogistics")
	String carrierId,
	@Schema(description = "배송 상태. NOT_TRACKED는 택배사가 아직 스캔하지 않아 조회되지 않음", example = "IN_TRANSIT")
	DeliveryTrackingStatus status,
	@Schema(description = "배송 상태 이름", example = "이동 중")
	String statusLabel,
	@Schema(description = "택배사가 붙인 상태 이름", example = "간선상차")
	String statusText,
	@Schema(description = "마지막 상태의 시각")
	LocalDateTime lastEventAt,
	@Schema(description = "마지막으로 조회한 시각. 아직 한 번도 조회하지 않았으면 null")
	LocalDateTime lastTrackedAt,
	@Schema(description = "이동 기록 (오래된 것부터)")
	List<Event> events
) {
	// 자동 조회하지 않는 배송(발송 전, BUK-55 전에 보낸 배송)은 상태가 없어 null이다
	public static KitTrackingDto of(String carrierId, DeliveryTrackingStatus status, String statusText,
		LocalDateTime lastEventAt, LocalDateTime lastTrackedAt, List<ShipmentTrackingEvent> events) {
		if (status == null) {
			return null;
		}
		return new KitTrackingDto(carrierId, status, status.getLabel(), statusText, lastEventAt, lastTrackedAt,
			events.stream().map(Event::from).toList());
	}

	public KitTrackingDto withEvents(List<ShipmentTrackingEvent> trackingEvents) {
		return new KitTrackingDto(carrierId, status, statusLabel, statusText, lastEventAt, lastTrackedAt,
			trackingEvents.stream().map(Event::from).toList());
	}

	@Schema(description = "이동 기록 한 줄")
	public record Event(
		LocalDateTime time,
		DeliveryTrackingStatus status,
		@Schema(description = "택배사가 붙인 상태 이름", example = "배달완료")
		String statusText,
		String description,
		String location
	) {
		static Event from(ShipmentTrackingEvent event) {
			return new Event(event.getEventAt(), event.getStatus(), event.getStatusText(), event.getDescription(),
				event.getLocation());
		}
	}
}
