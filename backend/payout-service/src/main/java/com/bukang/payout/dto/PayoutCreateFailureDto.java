package com.bukang.payout.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.bukang.payout.domain.PayoutCreateFailure;
import com.bukang.payout.domain.PayoutCreateFailureAction;
import com.bukang.payout.domain.PayoutCreateFailureType;
import com.bukang.payout.domain.PayoutStage;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 관리자 「이상 감지」 목록의 한 줄 (BUK-108). 판매자 · 대회는 ID만 준다. 이름은 화면이 다른 API로 붙인다
 */
@AllArgsConstructor
@Getter
public class PayoutCreateFailureDto {
	private final long id;
	private final LocalDateTime recordedAt;
	private final LocalDate runDate;
	private final PayoutStage payoutStage;
	private final Long sellerId;
	private final Long raceId;
	private final Long raceCourseId;
	private final LocalDate payoutTargetDate;
	private final PayoutCreateFailureType failureType;
	private final PayoutCreateFailureAction actionTaken;
	private final String note;

	public static PayoutCreateFailureDto from(PayoutCreateFailure failure) {
		return new PayoutCreateFailureDto(
			failure.getId(),
			failure.getCreateDate(),
			failure.getRunDate(),
			failure.getPayoutStage(),
			failure.getSellerId(),
			failure.getRaceId(),
			failure.getRaceCourseId(),
			failure.getPayoutTargetDate(),
			failure.getFailureType(),
			failure.getActionTaken(),
			failure.getNote()
		);
	}
}
