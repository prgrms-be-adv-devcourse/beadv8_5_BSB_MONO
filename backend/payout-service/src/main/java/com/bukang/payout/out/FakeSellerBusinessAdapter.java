package com.bukang.payout.out;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.bukang.common.shared.seller.domain.SellerType;

/**
 * 테스트용 어댑터: 모든 판매자를 사업자로 돌려준다 (BUK-246)
 * 운영 · 개발에서는 회원 서비스에 묻는 MemberSellerBusinessAdapter를 쓴다
 */
@Component
@Profile("test")
public class FakeSellerBusinessAdapter implements SellerBusinessPort {
	@Override
	public SellerBusiness find(long sellerId) {
		return new SellerBusiness(SellerType.BUSINESS, "테스트 상호", "123-45-67890", "테스트 대표");
	}
}
