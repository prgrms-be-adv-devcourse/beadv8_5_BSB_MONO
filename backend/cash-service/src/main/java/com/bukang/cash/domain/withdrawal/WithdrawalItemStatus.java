package com.bukang.cash.domain.withdrawal;

// 환급 한 번 안의 카드 몫 하나의 상태
public enum WithdrawalItemStatus {
	// PG 결제취소 결과를 모름. 재확인이 같은 중복 방지 키로 다시 요청해 맞춘다
	UNKNOWN,
	// PG 결제취소가 받아들여짐
	COMPLETED,
	// PG가 확실히 거절해 예치금에 되돌림
	REVERTED,
	// 카드로 돌려줄 수 없다는 거절(기간 초과·부분 취소 불가)이라 신청 때 받은 계좌로 보냄
	TRANSFERRED_TO_ACCOUNT
}
