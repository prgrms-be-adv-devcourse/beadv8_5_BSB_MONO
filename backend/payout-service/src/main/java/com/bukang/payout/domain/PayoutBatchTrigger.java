package com.bukang.payout.domain;

/**
 * 정산 배치를 누가 돌렸는지 (BUK-167). DB는 ENUM 칸이다 (PayoutEnumColumnTest)
 */
public enum PayoutBatchTrigger {
	스케줄,
	관리자
}
