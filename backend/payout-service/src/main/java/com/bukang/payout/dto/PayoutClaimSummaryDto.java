package com.bukang.payout.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 관리자 회수 관리 화면의 카드 숫자. 목록 필터와 상관없이 전체 청구 기준이다
 */
@AllArgsConstructor
@Getter
public class PayoutClaimSummaryDto {
	private final long claimingCount;
	// 회수할 금액 합계 = 청구 중인 청구 금액의 합
	private final long claimingAmount;
	// 이번 달(한국 시간) 입금을 확인한 청구 수
	private final long confirmedThisMonthCount;
}
