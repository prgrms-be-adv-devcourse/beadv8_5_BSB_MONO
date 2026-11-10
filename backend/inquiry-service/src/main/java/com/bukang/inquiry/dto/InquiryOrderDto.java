package com.bukang.inquiry.dto;

import java.util.List;

import com.bukang.inquiry.out.order.OrderSummary;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "문의에 묶인 주문 정보. 문의한 대회의 항목만 담는다. 열 때마다 주문 서비스에서 받는다")
public record InquiryOrderDto(
	@Schema(description = "주문번호", example = "OD-20261110-3F9A") String orderNo,
	@Schema(description = "결제 금액(원). 관리자 상세에만 준다", example = "50000", nullable = true) Long paymentAmount,
	@Schema(description = "주문 상태 (주문 서비스가 정한 값 그대로). 취소 여부를 여기서 본다", example = "PAID") String status,
	@Schema(description = "이 문의의 대회 항목") List<InquiryOrderItemDto> items
) {
	// 결제 금액은 시안대로 관리자 상세에만 준다
	// 주문 상태는 판매자에게도 준다. 발송 전에 취소된 키트도 배송 상태가 배송 준비(PREPARING)로 오기 때문이다 (PR #405)
	public static InquiryOrderDto of(OrderSummary order, long raceId, boolean withPayment) {
		List<InquiryOrderItemDto> items = order.items().stream()
			.filter(item -> item.raceId() == raceId)
			.map(InquiryOrderItemDto::from)
			.toList();
		Long paymentAmount = withPayment ? order.paymentAmount() : null;
		return new InquiryOrderDto(order.orderNo(), paymentAmount, order.status(), items);
	}
}
