package com.bukang.payout.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 판매자가 플랫폼에 돌려줄 정산금이 남았는지. 회원 서비스가 판매자 탈퇴를 막을 때 물어본다 (선 환불 후 회수 R11)
 * 피그잼 06-1 [1. 회원과 역할 / 4 탈퇴 / 2]: 돌려줄 정산금이 남은 판매자는 탈퇴할 수 없다
 */
@AllArgsConstructor
@Getter
public class SellerReceivableDto {
	private final long sellerId;
	private final boolean hasReceivable;
	// 갚을 돈이 남은 대회 ID. 없으면 빈 목록
	private final List<Long> raceIds;

	public static SellerReceivableDto of(long sellerId, List<Long> raceIds) {
		return new SellerReceivableDto(sellerId, !raceIds.isEmpty(), raceIds);
	}
}
