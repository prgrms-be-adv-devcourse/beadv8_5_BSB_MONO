package com.bukang.order.dto;

import java.time.LocalDateTime;

import com.bukang.order.domain.KitDeliveryStatus;
import com.bukang.order.domain.OrderItemStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class OrderItemDto {
	private final long id;
	private final long participationId;
	private final long raceCourseId;
	private final long sellerId;
	// 주문 당시 상품명·단가
	private final String itemName;
	private final long unitPrice;
	private final int quantity;
	private final long amount;
	private final OrderItemStatus status;
	// 키트 배송 상태 (PREPARING 배송 준비 / SHIPPING 배송 중 / DELIVERED 배송 완료). 키트가 없는 참가권은 null
	private final KitDeliveryStatus kitDeliveryStatus;
	// 판매자가 발송할 때 넣은 택배사·송장 번호. 발송 전이면 null
	private final String kitCarrier;
	private final String kitTrackingNumber;
	// 키트를 발송한 시각, 배송 완료로 바꾼 시각. 그 단계 전이면 null
	private final LocalDateTime kitShippedAt;
	private final LocalDateTime kitDeliveredAt;
}
