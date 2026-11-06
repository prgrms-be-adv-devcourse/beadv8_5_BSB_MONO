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
	public static final String ALREADY_JOINED = "ORDER_PARTY_ALREADY_JOINED";
	public static final String OTHER_PARTY_JOINED = "ORDER_PARTY_OTHER_PARTY_JOINED";
	public static final String FULL = "ORDER_PARTY_FULL";
	public static final String SALE_CLOSED = "ORDER_PARTY_SALE_CLOSED";
	public static final String SIZE_OUT_OF_RANGE = "ORDER_PARTY_SIZE_OUT_OF_RANGE";
	public static final String MEMBER_NOT_CREW = "ORDER_PARTY_MEMBER_NOT_CREW";
	public static final String MEMBER_JOINED_LATE = "ORDER_PARTY_MEMBER_JOINED_LATE";
	public static final String MEMBERS_CHANGED = "ORDER_PARTY_MEMBERS_CHANGED";

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

	// 파티 신청: 같은 파티는 한 번만 신청한다. 결제가 끝나거나 종료된 뒤에도 모집 중으로 돌아가지 않는다 (11/02 결정 ⑩)
	public static PartyConflictException alreadyApplied(long partyId, PartyStatus status) {
		return new PartyConflictException(NOT_RECRUITING,
			"이미 신청했거나 끝난 파티입니다(현재 상태: %s).".formatted(status.getLabel()), partyId, status);
	}

	public static PartyConflictException linkExpired() {
		return new PartyConflictException(LINK_EXPIRED, "참가 링크가 만료됐습니다. 파티장에게 새 링크를 받아주세요.", null, null);
	}

	public static PartyConflictException alreadyJoined(long partyId) {
		return new PartyConflictException(ALREADY_JOINED, "이미 이 파티에 합류했습니다.", partyId, null);
	}

	// 같은 대회의 다른 파티에 살아 있는 신청서가 있음. partyId는 그 다른 파티 (본인이 파티원이므로 알려도 된다)
	public static PartyConflictException otherPartyJoined(long otherPartyId) {
		return new PartyConflictException(OTHER_PARTY_JOINED,
			"다른 파티로 이 대회에 신청했습니다. 그 파티에서 나간 뒤 합류해 주세요.", otherPartyId, null);
	}

	// 최대 인원이 찼다 (판매자가 정한 crewMaxCount)
	public static PartyConflictException full(long partyId, int maxCount) {
		return new PartyConflictException(FULL,
			"파티 인원이 다 찼습니다(최대 %d명).".formatted(maxCount), partyId, null);
	}

	// 파티 신청 (BUK-37): 파티 예매 기간(크루 예매 시작~마감)이 아니거나 접수가 마감됨
	public static PartyConflictException saleClosed(long partyId) {
		return new PartyConflictException(SALE_CLOSED, "파티 예매 기간이 아닙니다.", partyId, null);
	}

	public static PartyConflictException sizeOutOfRange(long partyId, int size, int minCount, int maxCount) {
		return new PartyConflictException(SIZE_OUT_OF_RANGE,
			"파티 인원이 %d~%d명이어야 신청할 수 있습니다(지금 %d명).".formatted(minCount, maxCount, size), partyId, null);
	}

	public static PartyConflictException memberNotCrew(long partyId) {
		return new PartyConflictException(MEMBER_NOT_CREW,
			"크루를 떠난 파티원이 있어 신청할 수 없습니다.", partyId, null);
	}

	// 파티원 결제 (BUK-38): 결제하는 사람이 크루를 떠났다 (정책 6장)
	public static PartyConflictException payerNotCrew(long partyId) {
		return new PartyConflictException(MEMBER_NOT_CREW, "크루를 떠나 파티 결제를 할 수 없습니다.", partyId, null);
	}

	// 11/05 회의 P4: 파티 예매 시작 7일 전까지 가입한 크루원만 크루 몫으로 신청할 수 있다
	public static PartyConflictException memberJoinedLate(long partyId) {
		return new PartyConflictException(MEMBER_JOINED_LATE,
			"파티 예매 시작 7일 전보다 늦게 크루에 가입한 파티원이 있어 신청할 수 없습니다.", partyId, null);
	}

	// 확인하는 사이 누가 합류하거나 나갔다. 다시 누르면 바뀐 파티원으로 다시 확인한다
	public static PartyConflictException membersChanged(long partyId) {
		return new PartyConflictException(MEMBERS_CHANGED,
			"신청하는 사이 파티원이 바뀌었습니다. 다시 신청해 주세요.", partyId, null);
	}

	public boolean hasData() {
		return partyId != null;
	}
}
