package com.bukang.order.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.bukang.order.domain.OrderStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class OrderDto {
	private final long id;
	// 주문 일시
	private final LocalDateTime createDate;
	private final String orderNo;
	private final long buyerId;
	private final long raceId;
	private final OrderStatus status;
	private final long totalAmount;
	// 취소된 주문 상품 금액의 합 (PRO-46)
	private final long canceledAmount;
	// 결제 기한 (화면용 사본)
	private final LocalDateTime paymentDueAt;
	// 결제 완료 일시. 결제 전이면 null
	private final LocalDateTime paidAt;
	// 키트 배송지. 결제했으면 배송의 값, 결제 전이면 참가 신청의 값이다 (BUK-6). 키트가 없는 종목이면 null
	private final ShippingAddressDto shippingAddress;
	private final List<OrderItemDto> items;
}
