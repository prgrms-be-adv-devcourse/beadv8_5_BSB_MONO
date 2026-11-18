package com.bukang.order.dto;

import com.bukang.order.domain.OrderItemStatus;

/**
 * 참가권(주문 상품) 하나 (기록증 제출용 내부 응답, BUK-271). 대회·종목 ID는 주문 당시 값이다
 */
public record TicketDto(
	long buyerId,
	long raceId,
	long raceCourseId,
	OrderItemStatus status
) {
}
