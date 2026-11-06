package com.bukang.order.dto;

import java.time.LocalDateTime;

import com.bukang.order.domain.PartyStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 참가 링크로 본 파티 (BUK-39, 시안 08 「합류 확인」). 파티원 명단·참가자 정보·링크 토큰은 넣지 않는다
 * 프론트는 status·joinLinkAlive로 10 「링크 끝남」·12 「모집 끝남」을 고른다
 */
public record PartyPreviewDto(
	@Schema(description = "파티 ID", example = "1") long partyId,
	@Schema(description = "크루 ID", example = "7") long crewId,
	@Schema(description = "대회 ID", example = "100") long raceId,
	@Schema(description = "파티 종목 ID. 합류하면 이 종목으로 신청한다", example = "2") long raceCourseId,
	@Schema(description = "상태 RECRUITING(모집 중) / PAYING(결제 진행 중) / PAID(결제 끝) / CLOSED(종료). "
		+ "RECRUITING이 아니면 합류할 수 없다") PartyStatus status,
	@Schema(description = "파티장 닉네임. 회원 서비스에 없으면 「탈퇴한 회원」", example = "러너A") String leaderNickname,
	@Schema(description = "파티원 수 (파티장 포함)", example = "3") int memberCount,
	@Schema(description = "참가 링크 만료 시각 (만든 뒤 24시간)") LocalDateTime joinTokenExpiresAt,
	@Schema(description = "참가 링크가 아직 살아 있는지 (만료 시각 전)") boolean joinLinkAlive
) {
}
