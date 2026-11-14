package com.bukang.inquiry.out.race;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 가짜 어댑터: 상품 서비스를 띄우지 않고 개발할 때 쓴다. 운영은 http라 HttpRaceSellerAdapter가 답한다
 * 대회 ID 9999는 없는 대회이고, 그 밖의 대회는 판매자 회원 ID가 1이다
 * inquiry.clients.product.mode=fake라고 적었을 때만 켜진다. mode가 없으면 빈이 없어 앱이 뜨지 않는다 (matchIfMissing 없음)
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "inquiry.clients.product", name = "mode", havingValue = "fake")
public class FakeRaceSellerAdapter implements RaceSellerPort {
	static final long NOT_FOUND_RACE_ID = 9999L;
	static final long SELLER_MEMBER_ID = 1L;

	// 호출마다 경고를 남기면 로그가 묻히므로, 앱을 켤 때 한 번만 알린다
	public FakeRaceSellerAdapter() {
		log.warn("대회 판매자 조회는 가짜 응답(FakeRaceSellerAdapter)을 씁니다. 대회 9999는 없는 대회, 그 밖은 판매자 회원 1번입니다.");
	}

	@Override
	public Optional<Long> findSellerMemberId(long raceId) {
		if (raceId == NOT_FOUND_RACE_ID) {
			return Optional.empty();
		}
		return Optional.of(SELLER_MEMBER_ID);
	}
}
