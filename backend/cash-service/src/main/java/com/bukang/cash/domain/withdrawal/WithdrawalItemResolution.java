package com.bukang.cash.domain.withdrawal;

// 수동 확인 필요인 몫을 담당자가 PG 취소 기록을 보고 내린 결론
public enum WithdrawalItemResolution {
	// PG가 이미 카드로 돌려줬다. 몫을 완료로 바꾸고 충전 건의 취소한 금액에 더한다
	CARD_CANCELED,
	// PG가 취소하지 않았다. 예치금에 되돌린다
	NOT_CANCELED
}
