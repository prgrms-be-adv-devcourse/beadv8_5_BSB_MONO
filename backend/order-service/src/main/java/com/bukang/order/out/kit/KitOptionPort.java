package com.bukang.order.out.kit;

import java.util.List;
import java.util.Optional;

/**
 * 상품 서비스(B)의 대회 키트 옵션 목록 계약 (포트). 상품은 옵션과 준비 수량만 주고, 판매 수는 주문이 센다 (11/07 결정)
 */
@FunctionalInterface
public interface KitOptionPort {
	/**
	 * 대회의 키트 옵션을 모두 돌려준다 (등록 순). 옵션이 없는 대회는 빈 목록이다
	 * 상품이 대회를 찾지 못하면(404) 빈 값이다. 종목이 있는 대회라면 취소된 대회이거나 경로·주소가 어긋난 것이라,
	 * 부르는 쪽이 둘을 가린다 (옵션 없음으로 넘기면 옵션을 고르지 않은 결제가 통과한다)
	 */
	Optional<List<KitOption>> findKitOptions(long raceId);

	/**
	 * @param kitOptionId 상품의 옵션 ID
	 * @param name        옵션 이름 (예: T-shirt). 구매자는 이름마다 하나를 고른다
	 * @param value       옵션 값 (예: L)
	 * @param stock       준비 수량. null이면 맞춤 제작이라 품절이 없다
	 */
	record KitOption(long kitOptionId, String name, String value, Integer stock) {
	}
}
