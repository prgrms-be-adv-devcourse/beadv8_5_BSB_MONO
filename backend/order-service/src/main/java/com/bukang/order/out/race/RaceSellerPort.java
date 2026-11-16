package com.bukang.order.out.race;

import java.util.Optional;

/**
 * 대회 ID로 그 대회 판매자의 회원 ID를 찾는다. 판매자에게 키트 알림을 보낼 때 쓴다 (BUK-90, BUK-237)
 * 판매자 ID → 회원 ID API는 회원 서비스에 없어, 상품 서비스의 대회 판매자 조회(문의 서비스와 같은 API)를 쓴다
 * 읽기만 하므로 몇 번 불러도 된다. 연결하지 못하면 빈 값이 아니라 ExternalServiceUnavailableException(503)을 던진다
 */
@FunctionalInterface
public interface RaceSellerPort {
	/**
	 * @return 판매자의 회원 ID, 없는 대회면 빈 값
	 */
	Optional<Long> findSellerMemberId(long raceId);
}
