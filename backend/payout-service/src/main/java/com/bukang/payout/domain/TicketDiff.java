package com.bukang.payout.domain;

import java.util.List;

/**
 * 참가권 비교 결과. 두 목록 모두 작은 ID부터 담는다
 * @param missing 판매에는 있는데 정산 판매 줄에 없는 참가권 (빠짐)
 * @param extra 정산 판매 줄에만 있는 참가권 (더 들어감)
 */
public record TicketDiff(List<Long> missing, List<Long> extra) {
	public boolean isEmpty() {
		return missing.isEmpty() && extra.isEmpty();
	}
}
