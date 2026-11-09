package com.bukang.cash.app.withdrawal;

import java.util.List;

import com.bukang.cash.dto.payment.RefundableChargeDto;

/**
 * 환급 한 번의 카드 몫 계획. 정책 5장 2차 1 「PG 결제취소와 기한」:
 * 기한 안인 충전은 건별로 카드(PG 결제취소)로 돌려준다. 계좌 몫은 잔액에서 카드 몫을 뺀 나머지라
 * 정산 대금처럼 충전이 아닌 잔액까지 담도록 신청 쪽에서 계산한다
 */
record WithdrawalPlan(List<CardPart> cardParts) {
	record CardPart(long chargeId, long amount) {
	}

	static WithdrawalPlan from(List<RefundableChargeDto> charges) {
		return new WithdrawalPlan(charges.stream()
			.filter(charge -> charge.remainingAmount() > 0 && !charge.expired())
			.map(charge -> new CardPart(charge.chargeId(), charge.remainingAmount()))
			.toList());
	}
}
