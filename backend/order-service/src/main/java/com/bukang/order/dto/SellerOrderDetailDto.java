package com.bukang.order.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.bukang.common.shared.order.event.OrderItemRefundReason;
import com.bukang.order.domain.KitDeliveryStatus;
import com.bukang.order.domain.Order;
import com.bukang.order.domain.OrderItem;
import com.bukang.order.domain.OrderItemStatus;
import com.bukang.order.domain.Participation;
import com.bukang.order.domain.Shipment;

/**
 * 판매자 주문 상세 창 (CRU-39, 피그마 S03-4). 주문 상품(참가권 1장) 하나다
 * 배송지·연락처는 키트 배송 목록(SellerKitDto)과 같은 범위로 보여 준다. 키트가 없거나 결제 전이면 배송 칸이 모두 null이다
 * 대회 이름·대회일·환불 마감은 넣지 않는다. 화면이 상품 대회 상세 API로 붙인다
 */
public record SellerOrderDetailDto(
	long orderItemId,
	long orderId,
	String orderNo,
	LocalDateTime orderedAt,
	// 결제 전이면 null
	LocalDateTime paidAt,
	long raceId,
	long raceCourseId,
	// 주문 당시 종목 이름
	String courseName,
	OrderItemStatus status,
	String participantName,
	// 옛 티셔츠 사이즈 칸. 키트 옵션 전에 결제한 신청만 쓴다
	String tshirtSize,
	// 결제 때 고른 키트 옵션 사본 (고른 순). 없으면 빈 목록
	List<KitOptionChoiceDto> kitOptions,
	// 파티로 신청했으면 파티 ID, 개인 신청이면 null
	Long partyId,
	// 주문 당시 참가권 단가·장수·결제 금액
	long unitPrice,
	int quantity,
	long amount,
	// 취소를 마친 시각·환불한 금액·취소 이유. 취소가 아니면 null·0·null (취소 중이면 이유만 있다)
	LocalDateTime canceledAt,
	long canceledAmount,
	OrderItemRefundReason cancelReason,
	ShippingAddressDto shippingAddress,
	String deliveryMemo,
	// 보내기 전에 취소된 배송은 키트 배송 목록과 같이 PREPARING으로 보인다. status(CANCELED)로 취소를 알 수 있다
	KitDeliveryStatus kitDeliveryStatus,
	String kitCarrier,
	String kitTrackingNumber,
	LocalDateTime kitShippedAt,
	LocalDateTime kitDeliveredAt,
	LocalDateTime kitReceivedAt,
	KitTrackingDto kitTracking
) {
	public static SellerOrderDetailDto of(Order order, OrderItem item, Participation participation,
		Shipment shipment, List<KitOptionChoiceDto> kitOptions) {
		boolean kit = shipment != null;
		return new SellerOrderDetailDto(item.getId(), order.getId(), order.getOrderNo(), order.getCreateDate(),
			order.getPaidAt(), item.getRaceId(), item.getRaceCourseId(), item.getItemName(), item.getStatus(),
			participation.getParticipant().getName(), participation.getParticipant().getTshirtSize(), kitOptions,
			participation.getPartyId(), item.getUnitPrice(), item.getQuantity(), item.getAmount(),
			item.getCanceledAt(), item.getCanceledAmount(), item.getCancelReason(),
			kit ? ShippingAddressDto.from(shipment.getShippingAddress()) : null,
			kit ? shipment.getDeliveryMemo() : null,
			kit ? shipment.getStatus().getKitDeliveryStatus() : null,
			kit ? shipment.getCarrier() : null,
			kit ? shipment.getTrackingNumber() : null,
			kit ? shipment.getShippedAt() : null,
			kit ? shipment.getDeliveredAt() : null,
			kit ? shipment.getReceivedAt() : null,
			kit ? shipment.toTrackingDto() : null);
	}
}
