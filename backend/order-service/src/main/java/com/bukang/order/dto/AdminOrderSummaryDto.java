package com.bukang.order.dto;

import java.time.LocalDateTime;

import com.bukang.order.domain.OrderStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 관리자 회원·주문 조회의 한 줄 (BUK-26). 화면 열은 주문번호·상품·금액·상태·결제일이다
// 결제 대기·기한 만료·취소한 주문도 보여 주므로(문의 대응) 결제일은 비어 있을 수 있다
@AllArgsConstructor
@Getter
public class AdminOrderSummaryDto {
	private final long id;
	private final String orderNo;
	// 구매자 회원 ID. 주문번호로 찾았을 때 누구의 주문인지 알 수 있다
	private final long buyerId;
	// 주문 일시
	private final LocalDateTime createDate;
	// 첫 주문 상품의 주문 당시 상품명
	private final String itemName;
	private final int itemCount;
	private final long totalAmount;
	// 취소해 돌려준 금액 (부분 취소·취소)
	private final long canceledAmount;
	private final OrderStatus status;
	private final String statusLabel;
	// 결제 전이거나 결제하지 않고 끝난 주문은 null
	private final LocalDateTime paidAt;
}
