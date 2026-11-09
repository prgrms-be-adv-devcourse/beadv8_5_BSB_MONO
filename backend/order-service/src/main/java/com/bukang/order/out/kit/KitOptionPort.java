package com.bukang.order.out.kit;

import java.util.List;

/**
 * 상품 서비스(B)의 대회 키트 옵션 목록 계약 (포트). 상품은 옵션과 준비 수량만 주고, 판매 수는 주문이 센다 (11/07 결정)
 */
@FunctionalInterface
public interface KitOptionPort {
	/**
	 * 대회의 키트 옵션을 모두 돌려준다 (등록 순). 옵션이 없거나 없는 대회면 빈 목록이다
	 */
	List<KitOption> findKitOptions(long raceId);

	/**
	 * @param kitOptionId 상품의 옵션 ID
	 * @param name        옵션 이름 (예: T-shirt). 구매자는 이름마다 하나를 고른다
	 * @param value       옵션 값 (예: L)
	 * @param stock       준비 수량. null이면 맞춤 제작이라 품절이 없다
	 */
	record KitOption(long kitOptionId, String name, String value, Integer stock) {
	}
}
