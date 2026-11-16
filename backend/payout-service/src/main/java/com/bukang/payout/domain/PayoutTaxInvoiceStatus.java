package com.bukang.payout.domain;

/**
 * 수수료 세금계산서 상태 (BUK-246). DB는 ENUM 칸이다 (PayoutEnumColumnTest가 확인)
 */
public enum PayoutTaxInvoiceStatus {
	// 목업 승인번호를 받았다
	발행_완료,
	// 판매자 정보 조회나 발행 요청이 실패했다. 다음 배치가 다시 발행한다
	발행_실패
}
