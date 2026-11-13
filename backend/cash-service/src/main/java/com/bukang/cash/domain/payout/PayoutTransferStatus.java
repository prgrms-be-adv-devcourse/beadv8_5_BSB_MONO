package com.bukang.cash.domain.payout;

/**
 * 정산 지급 이동의 송금 상태. 수수료 이동은 지갑끼리 옮겨 바로 완료다
 * REQUESTED: 홀딩 지갑에서 빼고 은행에 보내기 전·결과 반영 전, UNKNOWN: 은행이 송금했는지 모름(재확인이 맞춘다)
 */
public enum PayoutTransferStatus {
	REQUESTED,
	UNKNOWN,
	COMPLETED,
	FAILED
}
