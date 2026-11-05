package com.bukang.cash.domain.wallet;

import java.util.EnumSet;
import java.util.Set;

/**
 * 프론트 "내역" 화면의 탭 묶음 (PRO-258). 회원 지갑에 쌓이는 LedgerType을 묶는다.
 * PAYOUT(정산수령__상품판매_대금)은 "전체"에서만 한 줄로 보여주고 탭은 따로 안 둔다(세미 범위, 프론트 확인).
 * 정산수령__상품판매_수수료는 판매자가 아니라 system 지갑(1번)에 쌓여 회원 지갑 조회엔 나오지 않으므로 뺐다.
 */
public enum LedgerCategory {
	CHARGE(EnumSet.of(LedgerType.충전__PG결제_토스페이먼츠)),
	PAYMENT(EnumSet.of(LedgerType.사용__주문결제)),
	REFUND(EnumSet.of(LedgerType.환불__주문취소)),
	WITHDRAWAL(EnumSet.of(
		LedgerType.출금__통장입금, LedgerType.출금취소__통장입금, LedgerType.출금__PG결제취소_토스페이먼츠)),
	PAYOUT(EnumSet.of(LedgerType.정산수령__상품판매_대금));

	private final Set<LedgerType> ledgerTypes;

	LedgerCategory(Set<LedgerType> ledgerTypes) {
		this.ledgerTypes = ledgerTypes;
	}

	public Set<LedgerType> ledgerTypes() {
		return ledgerTypes;
	}

	public static Set<LedgerType> allLedgerTypes() {
		Set<LedgerType> all = EnumSet.noneOf(LedgerType.class);
		for (LedgerCategory category : values()) {
			all.addAll(category.ledgerTypes);
		}
		return all;
	}

	public static LedgerCategory of(LedgerType ledgerType) {
		for (LedgerCategory category : values()) {
			if (category.ledgerTypes.contains(ledgerType)) {
				return category;
			}
		}
		throw new IllegalStateException("분류되지 않은 장부 유형입니다: " + ledgerType);
	}
}
