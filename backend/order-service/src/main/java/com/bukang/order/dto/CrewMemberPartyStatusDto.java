package com.bukang.order.dto;

import java.util.List;

import com.bukang.order.domain.PartyStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 크루 나가기·내보내기 전에 회원 서비스가 묻는 파티 상태 (11/05 회의 C1·C2, BUK-78)
 */
public record CrewMemberPartyStatusDto(
	@Schema(description = "크루를 나갈 수 있는지. 모집 중·결제 진행 중·결제 끝 파티에 들어 있으면 false (C1)", example = "false")
	boolean canLeave,
	@Schema(description = "크루장이 내보낼 수 있는지. 결제 진행 중·결제 끝 파티에 들어 있으면 false (C2)", example = "true")
	boolean canKick,
	@Schema(description = "막는 파티 목록 (파티 ID 순). 둘 다 true면 빈 목록")
	List<BlockingParty> blockingParties
) {
	public record BlockingParty(
		@Schema(description = "파티 ID", example = "7") long partyId,
		@Schema(description = "파티 상태 RECRUITING(모집 중) / PAYING(결제 진행 중) / PAID(결제 끝)", example = "RECRUITING")
		PartyStatus status
	) {
	}
}
