package com.bukang.order.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 파티 상태 (11/02 결정 ⑪). 모집 중 → 결제 진행 중 → 결제 끝 → 종료
 * 신청 전에 파티장이 떠나고 남은 사람이 없으면 모집 중에서 바로 종료가 된다. 결제 끝은 대회 다음 날 종료된다 (PartyCloseUseCase)
 */
@Getter
@RequiredArgsConstructor
public enum PartyStatus {
	RECRUITING("모집 중"),
	PAYING("결제 진행 중"),
	PAID("결제 끝"),
	CLOSED("종료");

	private final String label;
}
