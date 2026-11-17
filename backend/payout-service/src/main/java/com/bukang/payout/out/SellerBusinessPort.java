package com.bukang.payout.out;

import com.bukang.common.shared.seller.domain.SellerType;

/**
 * 판매자 사업자 정보를 받는 계약 (BUK-246). 수수료 세금계산서의 공급받는자 칸을 채운다
 * 발행할 때만 HTTP로 묻고 정산에 복사본을 두지 않는다. 받은 값은 세금계산서 행에 복사해 고정한다
 */
@FunctionalInterface
public interface SellerBusinessPort {
	/**
	 * @throws RuntimeException 판매자 정보를 받지 못함. 배치는 그 세금계산서를 발행 실패로 남기고 다음 날 다시 시도한다
	 */
	SellerBusiness find(long sellerId);

	record SellerBusiness(SellerType sellerType, String businessName, String businessNumber,
		String representativeName) {
		public boolean isBusiness() {
			return sellerType == SellerType.BUSINESS;
		}
	}
}
