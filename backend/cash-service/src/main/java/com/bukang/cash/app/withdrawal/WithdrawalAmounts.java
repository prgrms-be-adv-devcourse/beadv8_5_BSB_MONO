package com.bukang.cash.app.withdrawal;

import java.util.List;

import com.bukang.cash.domain.withdrawal.WalletWithdrawal;
import com.bukang.cash.domain.withdrawal.WalletWithdrawalItem;

// 몫별 상태로 다시 계산한 환급 금액. 같은 키 재요청 응답과 완료 알림이 같은 계산을 쓴다.
// accountAmount는 처음부터 계좌로 보낸 몫 + 카드에서 계좌로 바꾼 몫(switchedToAccountAmount)이다
record WithdrawalAmounts(
	long cardAmount, long accountAmount, long switchedToAccountAmount, long processingAmount, long revertedAmount
) {
	static WithdrawalAmounts of(WalletWithdrawal withdrawal, List<WalletWithdrawalItem> items) {
		long card = 0;
		long switched = 0;
		long processing = 0;
		long reverted = 0;
		for (WalletWithdrawalItem item : items) {
			switch (item.getStatus()) {
				case COMPLETED -> card += item.getAmount();
				case TRANSFERRED_TO_ACCOUNT -> switched += item.getAmount();
				// 수동 확인 필요도 회원에게는 처리 중으로 보인다
				case PENDING, UNKNOWN, MANUAL_CHECK -> processing += item.getAmount();
				case REVERTED -> reverted += item.getAmount();
				default -> {
				}
			}
		}
		long account = withdrawal.getAmount() - card - processing - reverted;
		return new WithdrawalAmounts(card, account, switched, processing, reverted);
	}
}
