package com.bukang.order.out.race;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 가짜 대회 판매자: 어느 대회든 시작 데이터 판매자(판매자 ID 7 = 회원 7, FakeSellerLookupAdapter와 같은 가정)의 회원 ID를 준다
 * order.clients.product.mode=fake(기본값)일 때만 쓰고, http면 HttpRaceSellerAdapter가 부른다
 */
@Component
@ConditionalOnProperty(prefix = "order.clients.product", name = "mode", havingValue = "fake", matchIfMissing = true)
public class FakeRaceSellerAdapter implements RaceSellerPort {
	static final long SELLER_MEMBER_ID = 7L;

	@Override
	public Optional<Long> findSellerMemberId(long raceId) {
		return Optional.of(SELLER_MEMBER_ID);
	}
}
