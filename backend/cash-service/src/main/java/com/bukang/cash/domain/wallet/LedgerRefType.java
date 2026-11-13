package com.bukang.cash.domain.wallet;

/**
 * 장부의 돈 이동을 일으킨 원인 객체 종류 (docs/erd/cash.md의 ref_type_code)
 * DB에 ERD 값을 그대로 저장하도록 상수 이름을 ERD 값과 같게 둔다.
 * DTO로 받은 문자열은 LedgerRefType.valueOf로 바꿔, 목록에 없는 값이면 IllegalArgumentException이 나게 한다.
 */
public enum LedgerRefType {
	WalletCharge,
	Order,
	Withdrawal,
	Payout,
	RecoveryDeposit
}
