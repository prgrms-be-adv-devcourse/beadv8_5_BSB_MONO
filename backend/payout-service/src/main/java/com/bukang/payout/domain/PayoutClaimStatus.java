package com.bukang.payout.domain;

/**
 * 청구 상태 (선 환불 후 회수 R3 · R4)
 */
public enum PayoutClaimStatus {
	// 판매자에게 입금을 요청했고 아직 관리자가 입금을 확인하지 않았다
	청구_중,
	// 관리자가 판매자 입금을 확인했다
	입금_확인;
}
