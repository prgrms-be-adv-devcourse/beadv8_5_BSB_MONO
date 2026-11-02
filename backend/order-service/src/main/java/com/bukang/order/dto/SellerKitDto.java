package com.bukang.order.dto;

import java.time.LocalDateTime;

import com.bukang.order.domain.KitDeliveryStatus;
import com.bukang.order.domain.OrderItemStatus;
import com.bukang.order.domain.ShipmentStatus;
import com.bukang.order.domain.ShippingAddress;

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
	private final String tshirtSize;
	private final ShippingAddressDto shippingAddress;
	private final KitDeliveryStatus kitDeliveryStatus;
	private final String kitCarrier;
	private final String kitTrackingNumber;
	private final LocalDateTime kitShippedAt;
	private final LocalDateTime kitDeliveredAt;

	// JPQL 생성자 조회용
	public SellerKitDto(long orderItemId, String orderNo, OrderItemStatus orderItemStatus, long raceId,
		long raceCourseId, String courseName, String participantName, String tshirtSize,
		ShippingAddress shippingAddress,
		ShipmentStatus shipmentStatus, String kitCarrier, String kitTrackingNumber,
		LocalDateTime kitShippedAt, LocalDateTime kitDeliveredAt) {
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
	}
}
