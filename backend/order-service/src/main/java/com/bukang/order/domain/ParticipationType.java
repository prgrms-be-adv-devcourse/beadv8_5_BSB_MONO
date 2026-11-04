package com.bukang.order.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 참가 신청 구분. 세미(1차)는 개인 신청(INDIVIDUAL)만 만든다. 파티원 신청(PARTY)은 파티 기능(BUK-35~)에서 만들고 party_id로 파티를 가리킨다
 */
@Getter
@RequiredArgsConstructor
public enum ParticipationType {
	INDIVIDUAL("개인"),
	PARTY("파티원");

	private final String label;
}
