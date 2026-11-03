package com.bukang.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 개인 신청이 있어 파티를 만들거나 합류할 수 없을 때(409 ORDER_INDIVIDUAL_PARTICIPATION_EXISTS)의 data
 *
 * @param participationId 남아 있는 개인 신청서 ID. 결제 전이면 POST /participations/{id}/cancel로 취소한 뒤 다시 합류한다
 */
public record IndividualParticipationExistsDto(
	@Schema(description = "남아 있는 개인 신청서 ID", example = "12")
	long participationId
) {
}
