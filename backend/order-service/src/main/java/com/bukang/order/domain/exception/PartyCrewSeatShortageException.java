package com.bukang.order.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

import lombok.Getter;

// 파티 신청 (BUK-37): 남은 크루 자리가 파티원 수보다 모자라 한 석도 잡지 않았다 (11/05 회의 P5: 전부 아니면 실패)
// 파티는 모집 중으로 남는다. 모자란 종목·필요한 수·남은 수를 응답 data에 담는다 (OrderExceptionHandler)
@Getter
public class PartyCrewSeatShortageException extends BusinessException {
	public static final String CODE = "ORDER_PARTY_CREW_SEAT_SHORTAGE";

	private final long partyId;
	private final long raceCourseId;
	private final int needed;
	private final int remaining;

	public PartyCrewSeatShortageException(long partyId, long raceCourseId, int needed, int remaining) {
		super(HttpStatus.CONFLICT, CODE,
			"남은 크루 자리가 %d석 모자랍니다(필요 %d석, 남은 %d석).".formatted(needed - remaining, needed, remaining));
		this.partyId = partyId;
		this.raceCourseId = raceCourseId;
		this.needed = needed;
		this.remaining = remaining;
	}
}
