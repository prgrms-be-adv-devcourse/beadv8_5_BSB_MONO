package com.bukang.product.domain;

/**
 * 크루 결제율 미달 기준 (BUK-176)
 * 파티가 확보한 크루 자리 중 결제한 자리가 25% 이하면 미달 1회다.
 * 한 크루가 한 달에 미달 3회 이상이면 30일 동안 파티를 만들 수 없다(제한 기록과 차단은 회원·주문 서비스가 한다)
 */
public final class CrewLowPaymentPolicy {
	// 이 비율(%) 이하면 미달이다
	public static final int LOW_RATE_PERCENT = 25;
	// 한 달에 이 횟수 이상 미달이면 제재 대상이다
	public static final int PENALTY_COUNT = 3;

	private CrewLowPaymentPolicy() {
	}

	// 소수 오차가 없게 정수로 비교한다: paid / held <= 25 / 100
	public static boolean isLow(long paidCount, long heldCount) {
		return paidCount * 100 <= heldCount * LOW_RATE_PERCENT;
	}

	public static boolean needsPenalty(int lowPaymentCount) {
		return lowPaymentCount >= PENALTY_COUNT;
	}
}
