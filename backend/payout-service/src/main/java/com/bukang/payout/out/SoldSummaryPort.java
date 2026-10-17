package com.bukang.payout.out;

import java.util.List;

/**
 * 1차 정산이 주문 서비스에서 받아야 하는 판매 집계의 계약 (포트, PRO-214)
 * 1차 정산금은 참가권 목록 없이 대회별 가격 합과 장수만으로 계산한다 (1차 정산금 = DOWN(가격 합 ÷ 장수 × 40%) × 장수)
 * 최종 정산은 참가권마다 판매 · 수수료 줄을 쓰므로 여전히 SoldEntryPort(참가권 목록)를 쓴다
 */
@FunctionalInterface
public interface SoldSummaryPort {
	/**
	 * 이 대회의 결제 완료 · 미취소 참가권을 판매자 × 코스별로 묶어 가격 합과 장수를 돌려준다
	 * 정산 대상일 전에 취소된 참가권은 넣지 않는다 (정산 전 취소, payout.md 1장 정산 대상일 5번)
	 */
	List<SoldSummary> summarizeSold(int raceId);
}
