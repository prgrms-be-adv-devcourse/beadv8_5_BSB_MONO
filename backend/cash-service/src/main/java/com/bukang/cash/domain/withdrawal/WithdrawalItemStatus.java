package com.bukang.cash.domain.withdrawal;

// 환급 한 번 안의 카드 몫 하나의 상태
public enum WithdrawalItemStatus {
	// 예치금은 뺐고 토스는 아직 부르지 않음(또는 부르는 중). 서버가 멈춰도 재확인이 이어받는다
	PENDING,
	// PG 결제취소 결과를 모름. 재확인이 같은 중복 방지 키로 다시 요청해 맞춘다
	UNKNOWN,
	// PG 결제취소가 받아들여짐
	COMPLETED,
	// PG가 확실히 거절해 예치금에 되돌림
	REVERTED,
	// 카드로 돌려줄 수 없다는 거절(기간 초과·부분 취소 불가 등)이라 신청 때 받은 계좌로 보냄
	TRANSFERRED_TO_ACCOUNT,
	// 이미 카드로 나갔을 수 있어(이미 취소됨류 거절, 결과 모름 7일 경과) 되돌리지 않고 사람이 확인한다.
	// 재확인 대상이 아니고, 이 몫이 있는 환급은 끝나지 않은 것으로 본다
	MANUAL_CHECK
}
