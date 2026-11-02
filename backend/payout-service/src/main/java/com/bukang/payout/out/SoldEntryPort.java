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
	 * 정산 대상일 전에 취소된 참가권은 넣지 않는다 (정산 전 취소, payout.md 1장 정산 대상일 5번)
	 */
	List<SoldEntry> findSoldEntries(long raceId);
}
