package com.bukang.payout.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 관리자 회수 관리 화면 응답: 카드 숫자와 청구 목록. 판매자 이름은 프론트가 회원 정보에서 따로 받아 합친다 (C6 결정)
 */
@AllArgsConstructor
@Getter
public class PayoutAdminClaimsDto {
	private final PayoutClaimSummaryDto summary;
	private final PageDto<PayoutClaimDto> claims;
}
