package com.bukang.payout.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 판매자가 아직 받지 않은 정산금이 남았는지. 회원 서비스가 판매자 탈퇴를 막을 때 물어본다
 * receivable(판매자가 갚을 돈)과 방향이 반대다: 이쪽은 판매자가 받을 돈이다
 */
@AllArgsConstructor
@Getter
public class SellerUnpaidDto {
	private final long sellerId;
	private final boolean hasUnpaid;

	public static SellerUnpaidDto of(long sellerId, boolean hasUnpaid) {
		return new SellerUnpaidDto(sellerId, hasUnpaid);
	}
}
