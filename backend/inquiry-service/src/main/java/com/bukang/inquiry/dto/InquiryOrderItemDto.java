package com.bukang.inquiry.dto;

import com.bukang.inquiry.out.order.OrderItemSummary;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "문의한 대회의 주문 항목")
public record InquiryOrderItemDto(
	@Schema(description = "대회 ID", example = "1") long raceId,
	@Schema(description = "대회 이름", example = "서울 마라톤") String raceName,
	@Schema(description = "코스 이름", example = "10K") String courseName,
	@Schema(description = "키트 요약. 키트가 없으면 null", example = "티셔츠 M, 완주 메달", nullable = true) String kitSummary,
	@Schema(description = "배송 상태 (주문 서비스가 정한 값 그대로). 키트가 없으면 null", example = "PREPARING",
		nullable = true) String deliveryStatus
) {
	public static InquiryOrderItemDto from(OrderItemSummary item) {
		return new InquiryOrderItemDto(item.raceId(), item.raceName(), item.courseName(), item.kitSummary(),
			item.deliveryStatus());
	}
}
