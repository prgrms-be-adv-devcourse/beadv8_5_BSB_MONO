package com.bukang.common.shared.seller.event;

import java.time.LocalDateTime;

import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.common.shared.seller.domain.SellerType;

/**
 * 판매자 변경 이벤트 (판매자 등록 신청, 승인·반려 때 발행하고, 다른 서비스가 받아 판매자 복제본에 덮어쓴다)
 * 브로커에 평문 JSON으로 남으므로 대회 상세에 공개하는 판매자 정보만 담는다. 정산 계좌, 연락처는 넣지 않는다.
 */
public record SellerChangedEvent(
	long id,
	long memberId,
	SellerType sellerType,
	String businessName,
	String businessNumber,
	String representativeName,
	SellerStatus status,
	LocalDateTime requestedAt,
	LocalDateTime reviewedAt
) {
}
