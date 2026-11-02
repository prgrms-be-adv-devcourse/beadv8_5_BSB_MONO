package com.bukang.member.dto;

import com.bukang.common.shared.seller.domain.SellerStatus;
import com.bukang.member.domain.Seller;

/**
 * 회원의 가장 최근 판매자 신청 (내부 API). 다른 서비스가 회원 ID로 판매자 ID(신청서 ID)를 찾을 때 쓴다
 * 신청한 적이 없으면 두 칸 모두 null이다. 승인 여부 판단은 부르는 쪽이 status로 한다
 *
 * @param sellerId 판매자 신청서 ID (MEMBER_SELLER.seller_id). 회원 ID와 다른 값이다
 * @param status   그 신청의 상태 (PENDING, APPROVED, REJECTED)
 */
public record MemberSellerDto(Long sellerId, SellerStatus status) {
	public static final MemberSellerDto NONE = new MemberSellerDto(null, null);

	public static MemberSellerDto from(Seller seller) {
		return new MemberSellerDto(seller.getId(), seller.getStatus());
	}
}
