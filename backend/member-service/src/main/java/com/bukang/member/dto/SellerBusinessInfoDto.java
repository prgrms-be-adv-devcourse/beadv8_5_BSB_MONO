package com.bukang.member.dto;

import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.member.domain.Seller;

/**
 * 판매자 사업자 정보 (내부 API, BUK-246). 정산 서비스가 수수료 세금계산서를 발행할 때 공급받는자 칸을 채우려고 받는다
 * 비사업자는 상호 · 사업자번호가 null이다. 발행 여부는 부르는 쪽이 sellerType으로 판단한다
 *
 * @param representativeName 사업자는 대표자, 비사업자는 성명
 */
public record SellerBusinessInfoDto(
	SellerType sellerType,
	String businessName,
	String businessNumber,
	String representativeName
) {
	public static SellerBusinessInfoDto from(Seller seller) {
		return new SellerBusinessInfoDto(seller.getSellerType(), seller.getBusinessName(), seller.getBusinessNumber(),
			seller.getRepresentativeName());
	}
}
