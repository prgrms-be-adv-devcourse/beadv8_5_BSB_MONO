package com.bukang.order.dto;

import java.time.LocalDateTime;

import com.bukang.order.domain.ParticipationStatus;
import com.bukang.order.domain.ParticipationType;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ParticipationDto {
	private final long id;
	private final LocalDateTime createDate;
	private final LocalDateTime modifyDate;
	private final long raceId;
	private final long raceCourseId;
	// 신청한 회원 ID
	private final long memberId;
	private final ParticipationType participationType;
	// 파티원 신청서면 그 파티 ID, 개인 신청이면 null. 합류 직후 「내 파티로 가기」에 쓴다 (BUK-36)
	private final Long partyId;
	private final ParticipationStatus status;
	private final ParticipantInfoDto participant;
	private final Boolean thirdPartyConsent;
	private final LocalDateTime consentedAt;
	// 자리 확보 전(PENDING)에는 null
	private final LocalDateTime paymentDueAt;
}
