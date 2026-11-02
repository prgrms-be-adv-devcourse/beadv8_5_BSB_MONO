package com.bukang.order.out.wallet;

/**
 * 예치금 서비스(D)의 결제(차감) 결과
 *
 * @param outcome  결제됨 / 잔액 부족
 * @param shortage 잔액 부족일 때 모자란 금액(원). 그 밖에는 0
 */
public record WalletPayResult(Outcome outcome, long shortage) {
	public enum Outcome {
		// 이번에 차감했거나, 같은 주문으로 이미 차감한 결과를 돌려받음 (D가 주문 ID로 멱등 처리)
		PAID,
		INSUFFICIENT_BALANCE
	}

	public static WalletPayResult paid() {
		return new WalletPayResult(Outcome.PAID, 0);
	}

	public static WalletPayResult insufficient(long shortage) {
		return new WalletPayResult(Outcome.INSUFFICIENT_BALANCE, shortage);
	}
}
