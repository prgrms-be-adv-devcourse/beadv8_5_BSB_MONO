package com.bukang.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 파티 만들기 응답: 파티와 파티장의 신청서
 */
public record PartyCreatedDto(
	@Schema(description = "파티") PartyDto party,
	@Schema(description = "파티장의 파티원 신청서 (결제 전)") ParticipationDto participation
) {
}
