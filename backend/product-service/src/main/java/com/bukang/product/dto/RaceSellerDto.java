package com.bukang.product.dto;

import com.bukang.common.shared.seller.domain.SellerType;
import com.bukang.product.domain.ProductSeller;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 대회 상세 아래에 보여 주는 판매자 정보 (상호명·사업자등록번호는 사업자만 있다)
@AllArgsConstructor
@Getter
public class RaceSellerDto {
	private final long sellerId;
	private final SellerType sellerType;
	private final String businessName;
	private final String businessNumber;
	private final String representativeName;

	public static RaceSellerDto from(ProductSeller seller) {
		return new RaceSellerDto(seller.getId(), seller.getSellerType(), seller.getBusinessName(),
			seller.getBusinessNumber(), seller.getRepresentativeName());
	}
}
