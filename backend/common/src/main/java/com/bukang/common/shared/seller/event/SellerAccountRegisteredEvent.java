package com.bukang.common.shared.seller.event;

/**
 * 판매자가 정산 계좌를 등록한 이벤트 (member-service가 발행하고, 정산 서비스가 받아 계좌 미등록 자동 보류를 푼다)
 * 브로커에 평문 JSON으로 남으므로 판매자 ID만 담는다. 계좌 번호 등 민감정보는 넣지 않는다.
 */
public record SellerAccountRegisteredEvent(
	int sellerId
) {
}
