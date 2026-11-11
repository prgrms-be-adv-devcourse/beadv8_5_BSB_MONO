package com.bukang.order.dto;

import java.util.List;

import com.bukang.order.dto.CrewMemberPartyStatusDto.BlockingParty;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 크루 없애기 전에 회원 서비스가 묻는 크루의 파티 상태 (11/09 결정, BUK-125)
 */
public record CrewPartyStatusDto(
	@Schema(description = "크루를 없앨 수 있는지. 모집 중·결제 진행 중·결제 끝 파티가 하나라도 있으면 false", example = "false")
	boolean canDelete,
	@Schema(description = "막는 파티 목록 (파티 ID 순). canDelete가 true면 빈 목록")
	List<BlockingParty> blockingParties
) {
}
