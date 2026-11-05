package com.bukang.order.dto;

import com.bukang.order.domain.PartyStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 파티 거절(409)의 data (BUK-36, 시안 v13 12·13·14)
 * ORDER_PARTY_NOT_RECRUITING: partyId·partyStatus, ORDER_PARTY_ALREADY_JOINED: partyId(합류한 파티),
 * ORDER_PARTY_OTHER_PARTY_JOINED: partyId(이미 신청한 다른 파티). 값이 없는 칸은 빠진다
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PartyConflictDto(
	@Schema(description = "파티 ID", example = "1")
	Long partyId,
	@Schema(description = "파티 상태 (모집이 끝난 파티 거절에만)", example = "PAYING")
	PartyStatus partyStatus
) {
}
