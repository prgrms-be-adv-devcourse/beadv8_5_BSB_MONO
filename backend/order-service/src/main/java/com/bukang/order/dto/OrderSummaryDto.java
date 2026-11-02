package com.bukang.order.dto;

import java.time.LocalDateTime;

import com.bukang.order.domain.OrderStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 주문 목록의 한 줄. 상품은 대표 상품명과 개수만 보여 주고, 자세한 내용은 주문 상세에서 본다
@AllArgsConstructor
@Getter
public class OrderSummaryDto {
	private final long id;
	// 주문 일시
	private final LocalDateTime createDate;
	private final String orderNo;
	private final OrderStatus status;
	private final long totalAmount;
	private final LocalDateTime paymentDueAt;
	// 첫 주문 상품의 주문 당시 상품명
	private final String itemName;
	private final int itemCount;
}
