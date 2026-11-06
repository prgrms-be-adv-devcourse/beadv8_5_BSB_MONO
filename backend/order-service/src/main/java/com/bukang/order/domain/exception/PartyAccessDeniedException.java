package com.bukang.order.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 파티에 할 수 없는 사람 (BUK-36). 크루원이 아니거나, 파티장만 할 수 있는 일이거나, 파티원이 아님
public class PartyAccessDeniedException extends BusinessException {
	public static final String CREW_MEMBER_REQUIRED = "ORDER_CREW_MEMBER_REQUIRED";
	public static final String LEADER_ONLY = "ORDER_PARTY_LEADER_ONLY";
	public static final String NOT_MEMBER = "ORDER_PARTY_NOT_MEMBER";

	private PartyAccessDeniedException(String code, String message) {
		super(HttpStatus.FORBIDDEN, code, message);
	}

	public static PartyAccessDeniedException crewMemberRequired() {
		return new PartyAccessDeniedException(CREW_MEMBER_REQUIRED, "이 크루의 크루원만 파티를 만들거나 합류할 수 있습니다.");
	}

	public static PartyAccessDeniedException leaderOnly() {
		return new PartyAccessDeniedException(LEADER_ONLY, "파티장만 할 수 있습니다.");
	}

	public static PartyAccessDeniedException notMember() {
		return new PartyAccessDeniedException(NOT_MEMBER, "이 파티의 파티원이 아닙니다.");
	}
}
