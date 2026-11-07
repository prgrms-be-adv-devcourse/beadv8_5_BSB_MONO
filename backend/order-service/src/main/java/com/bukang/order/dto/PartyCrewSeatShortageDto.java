package com.bukang.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 파티 신청이 크루 자리가 모자라 실패했을 때(409 ORDER_PARTY_CREW_SEAT_SHORTAGE)의 data (BUK-37)
 * 파티는 모집 중으로 남는다. 화면은 "10K 자리가 2석 모자라요"처럼 보여 준다
 */
public record PartyCrewSeatShortageDto(
	@Schema(description = "파티 ID", example = "1") long partyId,
	@Schema(description = "모자란 종목 ID (파티 종목)", example = "2") long raceCourseId,
	@Schema(description = "필요한 자리 수 (파티원 수)", example = "4") int needed,
	@Schema(description = "남은 크루 자리 수", example = "1") int remaining
) {
}
