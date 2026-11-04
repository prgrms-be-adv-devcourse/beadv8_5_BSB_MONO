package com.bukang.order.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;
import com.bukang.order.domain.PartyStatus;

import lombok.Getter;

// 파티 상태 때문에 지금 할 수 없는 요청 (BUK-36). code로 프론트가 원인을 나눈다
// 화면이 이어서 할 일을 보여 주도록 파티 번호·상태를 응답 data에 담는다 (시안 v13 12·13·14, OrderExceptionHandler)
@Getter
public class PartyConflictException extends BusinessException {
	public static final String NOT_RECRUITING = "ORDER_PARTY_NOT_RECRUITING";
	public static final String LINK_EXPIRED = "ORDER_PARTY_LINK_EXPIRED";
	public static final String RACE_MISMATCH = "ORDER_PARTY_RACE_MISMATCH";
	public static final String ALREADY_JOINED = "ORDER_PARTY_ALREADY_JOINED";
	public static final String OTHER_PARTY_JOINED = "ORDER_PARTY_OTHER_PARTY_JOINED";

	// 없으면 null (data 없이 응답)
	private final Long partyId;
	private final PartyStatus partyStatus;

	private PartyConflictException(String code, String message, Long partyId, PartyStatus partyStatus) {
		super(HttpStatus.CONFLICT, code, message);
		this.partyId = partyId;
		this.partyStatus = partyStatus;
	}

	public static PartyConflictException notRecruiting(long partyId, PartyStatus status) {
		return new PartyConflictException(NOT_RECRUITING,
			"모집 중인 파티가 아니라 파티원을 바꿀 수 없습니다(현재 상태: %s).".formatted(status.getLabel()),
			partyId, status);
	}

	public static PartyConflictException linkExpired() {
		return new PartyConflictException(LINK_EXPIRED, "참가 링크가 만료됐습니다. 파티장에게 새 링크를 받아주세요.", null, null);
	}

	public static PartyConflictException raceMismatch() {
		return new PartyConflictException(RACE_MISMATCH, "이 파티의 대회 종목만 고를 수 있습니다.", null, null);
	}

	public static PartyConflictException alreadyJoined(long partyId) {
		return new PartyConflictException(ALREADY_JOINED, "이미 이 파티에 합류했습니다.", partyId, null);
	}

	// 같은 대회의 다른 파티에 살아 있는 신청서가 있음. partyId는 그 다른 파티 (본인이 파티원이므로 알려도 된다)
	public static PartyConflictException otherPartyJoined(long otherPartyId) {
		return new PartyConflictException(OTHER_PARTY_JOINED,
			"다른 파티로 이 대회에 신청했습니다. 그 파티에서 나간 뒤 합류해 주세요.", otherPartyId, null);
	}

	public boolean hasData() {
		return partyId != null;
	}
}
