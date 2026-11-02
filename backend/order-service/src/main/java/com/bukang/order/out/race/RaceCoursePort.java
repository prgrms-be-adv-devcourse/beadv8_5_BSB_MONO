package com.bukang.order.out.race;

import java.util.Optional;

/**
 * 주문 서비스가 상품 서비스(B)에서 받아야 하는 종목 정보의 계약 (포트)
 * 상품 서비스가 생기면 이 인터페이스를 구현한 어댑터(HTTP 호출)를 붙인다. 참가 신청 코드는 바뀌지 않는다
 */
@FunctionalInterface
public interface RaceCoursePort {
	/**
	 * 종목 ID로 종목·대회 정보를 찾는다. 없거나 공개되지 않은 종목이면 빈 값을 돌려준다
	 */
	Optional<RaceCourseInfo> findCourse(long raceCourseId);
}
