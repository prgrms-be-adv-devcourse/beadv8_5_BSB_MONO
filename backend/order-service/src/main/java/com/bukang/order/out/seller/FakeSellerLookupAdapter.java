package com.bukang.order.out.seller;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 가짜 어댑터: 판매자 ID 조회를 실제로 붙이기 전까지 개발용으로 답한다 (PRO-197)
 * 회원 ID를 그대로 판매자 ID로 본다. 상품·정산의 시작 데이터 판매자(판매자 ID 7)가 회원 7이라 dev에서 그대로 맞는다
 * 누구나 판매자가 되므로 운영(prod)에서는 쓰지 않는다 (order.clients.seller.mode=disabled, DisabledSellerLookupAdapter)
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "order.clients.seller", name = "mode", havingValue = "fake", matchIfMissing = true)
public class FakeSellerLookupAdapter implements SellerLookupPort {
	// 호출마다 경고를 남기면 로그가 묻히므로, 앱을 켤 때 한 번만 알린다
	public FakeSellerLookupAdapter() {
		log.warn("판매자 ID 조회는 가짜 응답(FakeSellerLookupAdapter)을 씁니다. 회원 ID를 판매자 ID로 봅니다");
	}

	@Override
	public Optional<Long> findApprovedSellerId(long memberId) {
		return Optional.of(memberId);
	}
}
