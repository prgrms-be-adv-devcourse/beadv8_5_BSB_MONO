package com.bukang.inquiry.dto;

import java.util.List;

import com.bukang.inquiry.out.order.OrderSummary;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "문의에 묶인 주문 정보. 문의한 대회의 항목만 담는다. 열 때마다 주문 서비스에서 받는다")
public record InquiryOrderDto(
	@Schema(description = "주문번호", example = "OD-20261110-3F9A") String orderNo,
	@Schema(description = "결제 금액(원)", example = "50000") long paymentAmount,
	@Schema(description = "주문 상태 (주문 서비스가 정한 값 그대로)", example = "PAID") String status,
	@Schema(description = "이 문의의 대회 항목") List<InquiryOrderItemDto> items
) {
	public static InquiryOrderDto of(OrderSummary order, long raceId) {
		return new InquiryOrderDto(order.orderNo(), order.paymentAmount(), order.status(),
			order.items().stream().filter(item -> item.raceId() == raceId).map(InquiryOrderItemDto::from).toList());
	}
}
