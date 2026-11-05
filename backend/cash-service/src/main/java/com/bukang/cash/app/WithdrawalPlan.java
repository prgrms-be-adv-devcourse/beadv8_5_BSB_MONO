package com.bukang.cash.app;

import java.util.List;

import com.bukang.cash.dto.RefundableChargeDto;

/**
 * 환급 한 번을 몫으로 나눈 계획. 정책 5장 2차 1 「PG 결제취소와 기한」:
 * 기한 안인 충전은 건별로 카드(PG 결제취소)로, 기한이 지난 몫은 합쳐서 계좌로 돌려준다
 */
record WithdrawalPlan(List<CardPart> cardParts, long accountAmount) {
	record CardPart(long chargeId, long amount) {
	}

	static WithdrawalPlan from(List<RefundableChargeDto> charges) {
		List<CardPart> cardParts = charges.stream()
			.filter(charge -> charge.remainingAmount() > 0 && !charge.expired())
			.map(charge -> new CardPart(charge.chargeId(), charge.remainingAmount()))
			.toList();
		long accountAmount = charges.stream()
			.filter(RefundableChargeDto::expired)
			.mapToLong(RefundableChargeDto::remainingAmount)
			.sum();
		return new WithdrawalPlan(cardParts, accountAmount);
	}

	// 계좌로 보낼 몫이 있을 때만 회원에게 계좌 정보를 받는다
	boolean accountRequired() {
		return accountAmount > 0;
	}
}
