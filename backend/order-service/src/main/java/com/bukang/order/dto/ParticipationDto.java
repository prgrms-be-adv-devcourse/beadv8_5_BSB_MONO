package com.bukang.order.dto;

import java.time.LocalDateTime;
import java.util.List;

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
	// 결제 때 고른 키트 옵션 사본 (이름·값, 고른 순). 결제 전이거나 옵션이 없는 종목이면 빈 목록 (BUK-181)
	private final List<KitOptionChoiceDto> kitOptions;
}
