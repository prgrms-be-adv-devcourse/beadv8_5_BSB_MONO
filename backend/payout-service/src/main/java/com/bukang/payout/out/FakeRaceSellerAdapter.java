package com.bukang.payout.out;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 테스트용 어댑터: 상품 서비스를 띄우지 않는 테스트 프로파일에서 판매자를 찾지 못한 것으로 둔다 (보류 알림을 보내지 않음)
 * 보류 알림 테스트는 이 빈 대신 RaceSellerPort를 직접 넣는다
 */
@Component
@Profile("test")
public class FakeRaceSellerAdapter implements RaceSellerPort {
	@Override
	public Optional<Long> findSellerMemberId(long raceId) {
		return Optional.empty();
	}
}
