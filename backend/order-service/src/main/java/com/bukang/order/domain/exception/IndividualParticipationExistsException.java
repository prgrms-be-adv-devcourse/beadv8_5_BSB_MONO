package com.bukang.order.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

import lombok.Getter;

// 같은 대회에 끝나지 않은 개인 신청서가 있어 파티를 만들거나 합류할 수 없음 (11/03 결정: 거절하고 안내)
// 장바구니에서 빼도 결제 전 신청서는 남으므로, 그 신청서 ID를 응답 data.participationId로 준다 (OrderExceptionHandler)
// 프론트는 [개인 신청 취소](POST /participations/{id}/cancel)를 바로 보여 줄 수 있다
@Getter
public class IndividualParticipationExistsException extends BusinessException {
	public static final String CODE = "ORDER_INDIVIDUAL_PARTICIPATION_EXISTS";

	private final long participationId;

	public IndividualParticipationExistsException(long participationId) {
		super(HttpStatus.CONFLICT, CODE, "이 대회에 개인 신청이 있습니다. 개인 신청을 먼저 취소해 주세요.");
		this.participationId = participationId;
	}
}
