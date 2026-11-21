package com.bukang.order.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 파티에 할 수 없는 사람 (BUK-36). 크루원이 아니거나, 파티장만 할 수 있는 일. 파티원이 아니면 없는 파티와 같은 404다(CRU-91)
public class PartyAccessDeniedException extends BusinessException {
	public static final String CREW_MEMBER_REQUIRED = "ORDER_CREW_MEMBER_REQUIRED";
	public static final String LEADER_ONLY = "ORDER_PARTY_LEADER_ONLY";

	private PartyAccessDeniedException(String code, String message) {
		super(HttpStatus.FORBIDDEN, code, message);
	}

	public static PartyAccessDeniedException crewMemberRequired() {
		return new PartyAccessDeniedException(CREW_MEMBER_REQUIRED, "이 크루의 크루원만 파티를 만들거나 합류할 수 있습니다.");
	}

	public static PartyAccessDeniedException leaderOnly() {
		return new PartyAccessDeniedException(LEADER_ONLY, "파티장만 할 수 있습니다.");
	}
}
