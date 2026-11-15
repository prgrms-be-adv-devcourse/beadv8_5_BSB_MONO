package com.bukang.product.out;

/**
 * 파티 하나의 결제 집계 (크루 결제율 미달 판정용, BUK-176)
 * heldCount는 파티가 확보한 자리 수, paidCount는 그중 판매된 채로 남은 자리 수다
 */
public interface CrewPartyPaymentRow {
	long getPartyId();

	long getCrewId();

	long getHeldCount();

	long getPaidCount();
}
