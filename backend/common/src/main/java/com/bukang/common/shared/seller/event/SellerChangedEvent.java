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
	LocalDateTime reviewedAt,
	String eventId
) {
	// 받는 쪽 초기 데이터·테스트 전용. 회원 서비스는 쓰지 않는다 (eventId가 비어 아웃박스 저장에서 예외가 난다)
	public SellerChangedEvent(long id, long memberId, SellerType sellerType, String businessName,
		String businessNumber, String representativeName, SellerStatus status, LocalDateTime requestedAt,
		LocalDateTime reviewedAt) {
		this(id, memberId, sellerType, businessName, businessNumber, representativeName, status, requestedAt,
			reviewedAt, null);
	}
}
