package com.bukang.order.dto;

import com.bukang.order.domain.Party;
import com.bukang.order.domain.PartyStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 이 대회에서 내가 파티장인 파티 (BUK-91). 프론트가 [대회 참가 신청]을 누를 때 파티 대기열에 넣기 전에 본다
 * 파티장이 아니면 leader=false만 있고 나머지 칸은 빠진다
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PartyLeaderDto(
	@Schema(description = "이 대회에서 끝나지 않은(종료 아닌) 파티의 파티장인지", example = "true")
	boolean leader,
	@Schema(description = "지금 신청할 수 있는지. 파티장이고 파티가 모집 중일 때만 true. 이때만 파티 대기열에 넣는다",
		example = "true")
	boolean canApply,
	@Schema(description = "파티 ID (파티장일 때만)", example = "1")
	Long partyId,
	@Schema(description = "파티 종목 ID (파티장일 때만)", example = "2")
	Long raceCourseId,
	@Schema(description = "파티 상태 (파티장일 때만) RECRUITING(모집 중) / PAYING(결제 진행 중) / PAID(결제 끝)",
		example = "RECRUITING")
	PartyStatus partyStatus
) {
	public static PartyLeaderDto notLeader() {
		return new PartyLeaderDto(false, false, null, null, null);
	}

	public static PartyLeaderDto of(Party party) {
		return new PartyLeaderDto(true, party.getStatus() == PartyStatus.RECRUITING, party.getId(),
			party.getRaceCourseId(), party.getStatus());
	}
}
