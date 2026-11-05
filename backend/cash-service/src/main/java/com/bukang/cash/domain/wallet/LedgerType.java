package com.bukang.cash.domain.wallet;

/**
 * 예치금 장부 거래 유형 (docs/erd/cash.md의 "ledger_type 값")
 * DB에 ERD 값을 그대로 저장하도록 상수 이름을 ERD 값과 같게 둔다. 금액 부호는 docs/erd/cash.md의 장부 기록 규칙을 따른다.
 */
public enum LedgerType {
	충전__PG결제_토스페이먼츠,
	출금__통장입금,
	사용__주문결제,
	임시보관__주문결제,
	정산지급__상품판매_수수료,
	정산수령__상품판매_수수료,
	정산지급__상품판매_대금,
	정산수령__상품판매_대금,
	환불__주문취소,
	임시보관반환__주문취소,
	출금취소__통장입금,
	출금__PG결제취소_토스페이먼츠
}
