package com.bukang.product.dto;

import com.bukang.product.domain.Race;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 대회 판매자의 회원 ID (문의 서비스가 문의를 받을 사람으로 쓴다, BUK-159)
// 판매자 ID(product_seller.id)가 아니라 그 판매자를 신청한 회원의 ID다
@AllArgsConstructor
@Getter
public class RaceSellerMemberDto {
	private final long sellerMemberId;

	public static RaceSellerMemberDto from(Race race) {
		return new RaceSellerMemberDto(race.getSeller().getMember().getId());
	}
}
