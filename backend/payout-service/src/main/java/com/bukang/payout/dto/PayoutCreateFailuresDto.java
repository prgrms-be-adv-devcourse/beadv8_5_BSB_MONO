package com.bukang.payout.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 관리자 「이상 감지」 화면 (BUK-108). 카드 숫자는 종류 선택과 상관없이 기간 전체로 세고, 목록만 종류로 거른다
 */
@AllArgsConstructor
@Getter
public class PayoutCreateFailuresDto {
	// 대사 불일치 건수
	private final long mismatchCount;
	// 자동으로 채운 참가권: 빠진 참가권을 채운 대사 불일치 건수 (판매자 × 대회 줄 수, 장수 아님)
	private final long autoFilledCount;
	// 주문 조회 실패 건수
	private final long orderLookupFailureCount;
	private final PageDto<PayoutCreateFailureDto> records;
}
