package com.bukang.order.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.bukang.order.domain.DeliveryTrackingStatus;
import com.bukang.order.domain.KitDeliveryStatus;
import com.bukang.order.domain.OrderItemStatus;
import com.bukang.order.domain.ShipmentStatus;
import com.bukang.order.domain.ShipmentTrackingEvent;
import com.bukang.order.domain.ShippingAddress;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;

/**
 * 판매자 키트 배송 관리 목록의 한 줄 (시안 S08, PRO-136). 배송 상자에 든 참가권 중 발송 전에 취소되지 않은 것만 나온다
 * 키트·배송 칸은 배송(ORDER_SHIPMENT)의 값이다 (BUK-6). 응답 모양은 배송 표 전과 같다
 * 구매자 실명·연락처·배송지는 배송에 필요한 범위라 판매자에게 보여 준다 (참가 신청 때 제3자 제공 동의)
 */
@Getter
public class SellerKitDto {
	private final long orderItemId;
	private final String orderNo;
	// 참가권 상태. 보낸 뒤 취소된 참가권은 CANCELED로 보인다
	private final OrderItemStatus orderItemStatus;
	private final long raceId;
	private final long raceCourseId;
	// 주문 당시 종목 이름
	private final String courseName;
	private final String participantName;
	// 옛 티셔츠 사이즈 칸. 키트 옵션(kitOptions)으로 옮기는 동안만 둔다. 옵션을 고른 신청은 kitOptions를 본다
	private final String tshirtSize;
	// 결제 때 고른 키트 옵션 사본 (이름·값, 고른 순). 옵션 전에 결제한 신청은 비어 있고 tshirtSize를 쓴다
	private List<KitOptionChoiceDto> kitOptions = List.of();
	private final ShippingAddressDto shippingAddress;
	private final KitDeliveryStatus kitDeliveryStatus;
	private final String kitCarrier;
	private final String kitTrackingNumber;
	private final LocalDateTime kitShippedAt;
	private final LocalDateTime kitDeliveredAt;
	// Delivery Tracker에서 받아 온 배송 상태와 이동 기록 (BUK-55). 이동 기록은 목록을 읽은 뒤 한 번에 채운다 (attachTrackingEvents)
	private KitTrackingDto kitTracking;
	@JsonIgnore
	private final long shipmentId;
	@JsonIgnore
	private final long participationId;

	// JPQL 생성자 조회용
	public SellerKitDto(long orderItemId, String orderNo, OrderItemStatus orderItemStatus, long raceId,
		long raceCourseId, String courseName, String participantName, String tshirtSize,
		ShippingAddress shippingAddress,
		ShipmentStatus shipmentStatus, String kitCarrier, String kitTrackingNumber,
		LocalDateTime kitShippedAt, LocalDateTime kitDeliveredAt,
		long shipmentId, String carrierId, DeliveryTrackingStatus trackingStatus, String trackingStatusText,
		LocalDateTime lastEventAt, LocalDateTime lastTrackedAt, long participationId) {
		this.orderItemId = orderItemId;
		this.orderNo = orderNo;
		this.orderItemStatus = orderItemStatus;
		this.raceId = raceId;
		this.raceCourseId = raceCourseId;
		this.courseName = courseName;
		this.participantName = participantName;
		this.tshirtSize = tshirtSize;
		this.shippingAddress = ShippingAddressDto.from(shippingAddress);
		this.kitDeliveryStatus = shipmentStatus.getKitDeliveryStatus();
		this.kitCarrier = kitCarrier;
		this.kitTrackingNumber = kitTrackingNumber;
		this.kitShippedAt = kitShippedAt;
		this.kitDeliveredAt = kitDeliveredAt;
		this.shipmentId = shipmentId;
		this.participationId = participationId;
		this.kitTracking = KitTrackingDto.of(carrierId, trackingStatus, trackingStatusText, lastEventAt, lastTrackedAt,
			List.of());
	}

	public void attachKitOptions(List<KitOptionChoiceDto> kitOptions) {
		this.kitOptions = kitOptions;
	}

	// 이 줄 배송의 이동 기록(오래된 것부터)을 붙인다
	public void attachTrackingEvents(List<ShipmentTrackingEvent> events) {
		if (kitTracking != null) {
			this.kitTracking = kitTracking.withEvents(events);
		}
	}
}
