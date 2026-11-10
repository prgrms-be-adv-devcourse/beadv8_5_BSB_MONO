package com.bukang.inquiry.dto;

import java.util.List;

import com.bukang.inquiry.out.order.OrderSummary;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "문의에 묶인 주문 정보. 문의한 대회의 항목만 담는다. 열 때마다 주문 서비스에서 받는다")
public record InquiryOrderDto(
	@Schema(description = "주문번호", example = "OD-20261110-3F9A") String orderNo,
	@Schema(description = "결제 금액(원). 관리자 상세에만 준다", example = "50000", nullable = true) Long paymentAmount,
	@Schema(description = "주문 상태 (주문 서비스가 정한 값 그대로). 관리자 상세에만 준다", example = "PAID", nullable = true)
	String status,
	@Schema(description = "이 문의의 대회 항목") List<InquiryOrderItemDto> items
) {
	// 한 주문에 다른 판매자의 대회가 함께 있을 수 있어, 주문 전체 값(결제 금액·주문 상태)은 관리자에게만 준다
	public static InquiryOrderDto of(OrderSummary order, long raceId, boolean withOrderTotals) {
		List<InquiryOrderItemDto> items = order.items().stream()
			.filter(item -> item.raceId() == raceId)
			.map(InquiryOrderItemDto::from)
			.toList();
		if (!withOrderTotals) {
			return new InquiryOrderDto(order.orderNo(), null, null, items);
		}
		return new InquiryOrderDto(order.orderNo(), order.paymentAmount(), order.status(), items);
	}
}
