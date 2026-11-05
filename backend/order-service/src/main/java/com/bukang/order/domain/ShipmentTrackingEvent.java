package com.bukang.order.domain;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;
import java.util.Objects;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;
import com.bukang.order.out.delivery.DeliveryTrackingResult;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 배송의 이동 기록 한 줄 (BUK-55). Delivery Tracker에서 받아 온 값을 그대로 남긴다. 같은 기록은 한 줄만 둔다(Shipment.applyTracking)
 * 상대가 보낸 글이라 칸보다 길면 잘라 저장한다
 */
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(name = "ORDER_SHIPMENT_TRACKING_EVENT")
public class ShipmentTrackingEvent extends BaseIdAndTime {
	private static final int MAX_STATUS_TEXT_LENGTH = 50;
	private static final int MAX_DESCRIPTION_LENGTH = 255;
	private static final int MAX_LOCATION_LENGTH = 100;

	@ManyToOne(fetch = LAZY, optional = false)
	@JoinColumn(name = "shipment_id", nullable = false)
	private Shipment shipment;

	@Column(name = "event_at")
	private LocalDateTime eventAt;

	@Enumerated(STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(30)")
	private DeliveryTrackingStatus status;

	@Column(name = "status_text", length = MAX_STATUS_TEXT_LENGTH)
	private String statusText;

	@Column(name = "description", length = MAX_DESCRIPTION_LENGTH)
	private String description;

	@Column(name = "location", length = MAX_LOCATION_LENGTH)
	private String location;

	ShipmentTrackingEvent(Shipment shipment, DeliveryTrackingResult.Event event) {
		this.shipment = shipment;
		this.eventAt = event.time();
		this.status = event.status();
		this.statusText = cut(event.statusText(), MAX_STATUS_TEXT_LENGTH);
		this.description = cut(event.description(), MAX_DESCRIPTION_LENGTH);
		this.location = cut(event.location(), MAX_LOCATION_LENGTH);
	}

	// 이미 저장한 기록과 같은지 (자른 뒤의 값으로 비교한다)
	boolean isSameAs(DeliveryTrackingResult.Event event) {
		return Objects.equals(eventAt, event.time()) && status == event.status()
			&& Objects.equals(description, cut(event.description(), MAX_DESCRIPTION_LENGTH))
			&& Objects.equals(location, cut(event.location(), MAX_LOCATION_LENGTH));
	}

	// 글자(코드 포인트) 단위로 자른다. UTF-16 단위로 자르면 이모지가 반쪽 남아 MySQL이 저장을 거절한다
	static String cut(String value, int maxLength) {
		if (value == null || value.codePointCount(0, value.length()) <= maxLength) {
			return value;
		}
		return value.substring(0, value.offsetByCodePoints(0, maxLength));
	}
}
