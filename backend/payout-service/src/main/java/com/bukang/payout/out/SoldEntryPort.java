package com.bukang.payout.out;

import java.util.List;

/**
 * 최종 정산이 주문 서비스에서 받아야 하는 판매 내역(참가권 목록)의 계약 (포트)
 * 대상 대회는 상품 서비스가 대회일로 정하므로, 판매 내역은 그 대회의 ID로 묻는다 (1차의 SoldSummaryPort와 같은 방식)
 */
@FunctionalInterface
public interface SoldEntryPort {
	/**
	 * 이 대회에서 결제되고 취소되지 않은 참가권을 돌려준다
	 * 정산 생성일 전에 취소된 참가권은 넣지 않는다 (정산 전 취소)
	 */
	List<SoldEntry> findSoldEntries(long raceId);

	/**
	 * 같은 판매 내역을 코스 묶음 그대로 돌려준다. 최종 정산 생성이 쓴다 (참가권 목록으로 풀지 않아 메모리를 덜 쓴다)
	 * 기본은 목록을 묶는다. 주문 어댑터는 응답 묶음을 그대로 넘긴다
	 */
	default SoldCourses findSoldCourses(long raceId) {
		return SoldCourses.of(findSoldEntries(raceId));
	}
}
