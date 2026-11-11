package com.bukang.payout.out;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 테스트용 어댑터: 모든 판매자가 계좌를 등록했다고 돌려준다
 * 운영 · 개발에서는 회원 서비스에 묻는 MemberSellerAccountAdapter를 쓴다 (BUK-63)
 */
@Component
@Profile("test")
public class NoSellerAccountAdapter implements SellerAccountPort {
	@Override
	public boolean hasPayoutAccount(long sellerId) {
		return true;
	}
}
