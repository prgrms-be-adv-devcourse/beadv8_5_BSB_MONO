package com.bukang.order.out.seller;

import java.util.Optional;

/**
 * 회원 ID로 그 회원의 승인된 판매자 ID를 찾는다 (판매자 키트 API, PRO-197)
 * 게이트웨이는 회원 ID만 넘겨 주는데, 주문 상품에는 판매자 ID(seller_id)가 있어 둘을 이어야 한다
 * 판매자 원본은 회원 서비스, 사본은 상품 서비스에 있고 모두 같은 판매자 ID를 쓴다 (queuing.seller.changed)
 * 실제 연결은 나중에 어댑터만 붙인다: 상품 내부 API를 부르는 HTTP 어댑터, 또는 판매자 이벤트를 받아 주문에 둔 사본
 * 읽기만 하므로 몇 번 불러도 된다. 연결하지 못하면 빈 값이 아니라 ExternalServiceUnavailableException(503)을 던진다
 */
public interface SellerLookupPort {
	/**
	 * @return 승인된 판매자면 판매자 ID, 판매자가 아니거나 승인 전·반려면 빈 값
	 */
	Optional<Long> findApprovedSellerId(long memberId);
}
