package com.bukang.order.dto;

import java.util.List;

/**
 * 문의 서비스(BUK-154)가 문의를 만들 때 주인·대회를 확인하고, 판매자·관리자 상세에 보여 줄 주문 요약 (BUK-160)
 * 필드는 문의 서비스 OrderSummary·OrderItemSummary와 같다. 상태 값은 주문 서비스의 enum 이름 그대로다
 *
 * @param memberId      주문한 회원 ID
 * @param orderNo       주문번호 (예: OD-20261110-3F9A)
 * @param paymentAmount 결제한 금액(원). 취소해도 줄이지 않는다 (취소액은 주문 상세의 canceledAmount)
 * @param status        주문 상태 (OrderStatus 이름)
 * @param items         주문 상품마다 한 줄
 */
public record OrderLookupDto(long memberId, String orderNo, long paymentAmount, String status,
	List<Item> items) {

	/**
	 * @param raceId         대회 ID
	 * @param raceName       대회명. 상품 서비스에서 받지 못하면 주문 때 남긴 상품명("대회명 종목명")
	 * @param courseName     종목명 (예: 10K). 상품 서비스에서 받지 못하면 null
	 * @param kitSummary     고른 키트 옵션 (예: "T-shirt L, 양말 260"). 키트가 없으면 null
	 * @param deliveryStatus 키트 배송 상태 (KitDeliveryStatus 이름). 키트가 없으면 null
	 */
	public record Item(long raceId, String raceName, String courseName, String kitSummary, String deliveryStatus) {
	}
}
