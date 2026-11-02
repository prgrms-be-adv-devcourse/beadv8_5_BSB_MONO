package com.bukang.order.dto;

import java.time.LocalDateTime;

// 환불 결과를 몰라 취소 중으로 남은 주문 상품 하나 (취소 재처리 대상, BUK-9). 환불은 주문 ID·구매자 ID로 다시 부른다
public record CancelingOrderItemDto(long orderId, long orderItemId, long buyerId, LocalDateTime cancelRequestedAt) {
}
