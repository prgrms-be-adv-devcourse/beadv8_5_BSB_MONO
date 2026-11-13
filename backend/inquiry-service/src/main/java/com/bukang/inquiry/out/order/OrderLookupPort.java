package com.bukang.inquiry.out.order;

import java.util.Optional;

/**
 * 주문 ID로 주문 요약을 주문 서비스에 묻는다. 문의를 만들 때(주인·대회 확인)와 판매자·관리자 상세를 열 때(주문 정보 보여 주기) 부른다
 * 배송 상태가 바뀌므로 저장하지 않고 열 때마다 묻는다. 읽기만 하므로 몇 번 불러도 된다
 * 주문이 없으면 빈 값이고, 연결하지 못하면 빈 값이 아니라 InquiryExternalServiceUnavailableException(503)을 던진다
 * (문의를 만들 때는 그대로 503이고, 상세를 열 때는 부르는 쪽이 주문 정보만 비운다)
 *
 * 주문 담당자에게 요청할 내부 API 계약 (아직 없음. 주소는 제안):
 * GET /internal/v1/order/orders/{orderId}/summary → RsData.data
 * {memberId, orderNo, paymentAmount, status, items: [{raceId, raceName, courseName, kitSummary, deliveryStatus}]}
 * - 주문이 없으면 404 (코드 ORDER_NOT_FOUND 제안)
 * - status·deliveryStatus는 주문 서비스의 상태 이름을 그대로 문자열로 준다
 * - 키트가 없는 항목은 kitSummary·deliveryStatus가 null
 */
public interface OrderLookupPort {
	Optional<OrderSummary> findOrder(long orderId);
}
