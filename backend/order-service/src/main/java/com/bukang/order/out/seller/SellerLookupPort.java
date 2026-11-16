package com.bukang.order.out.seller;

import java.util.Optional;

/**
 * 회원 ID로 그 회원의 승인된 판매자 ID를 찾는다 (판매자 키트 API, PRO-197)
 * 게이트웨이는 회원 ID만 넘겨 주는데, 주문 상품에는 판매자 ID(seller_id)가 있어 둘을 이어야 한다
 * 판매자 원본은 회원 서비스에 있어 그 내부 API를 부른다 (HttpSellerLookupAdapter, BUK-237)
 * 반대 방향(판매자에게 알림을 보낼 회원 ID)은 상품 서비스의 대회 판매자 조회를 쓴다 (RaceSellerPort)
 * 읽기만 하므로 몇 번 불러도 된다. 연결하지 못하면 빈 값이 아니라 ExternalServiceUnavailableException(503)을 던진다
 */
@FunctionalInterface
public interface SellerLookupPort {
	/**
	 * @return 승인된 판매자면 판매자 ID, 판매자가 아니거나 승인 전·반려면 빈 값
	 */
	Optional<Long> findApprovedSellerId(long memberId);
}
