package com.bukang.order.dto;

import java.time.LocalDateTime;

import com.bukang.order.domain.PartyStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 파티 카드 (BUK-39). 내 파티 목록(시안 19·M12)과 크루 홈 「진행 중인 파티」(시안 19·M09)에 쓴다
 * 참가 링크 토큰과 파티원 명단은 넣지 않는다
 */
public record PartySummaryDto(
	@Schema(description = "파티 ID", example = "1") long id,
	@Schema(description = "크루 ID", example = "7") long crewId,
	@Schema(description = "대회 ID", example = "100") long raceId,
	@Schema(description = "파티 종목 ID", example = "2") long raceCourseId,
	@Schema(description = "상태 RECRUITING(모집 중) / PAYING(결제 진행 중) / PAID(결제 끝) / CLOSED(종료)") PartyStatus status,
	@Schema(description = "파티장 회원 ID", example = "3") long leaderMemberId,
	@Schema(description = "파티장 닉네임. 회원 서비스에 없으면 「탈퇴한 회원」", example = "러너A") String leaderNickname,
	@Schema(description = "파티원 수 (파티장 포함)", example = "3") int memberCount,
	@Schema(description = "결제를 마친(참가 확정) 파티원 수", example = "1") int paidCount,
	@Schema(description = "파티원 결제 기한. 신청 전이면 null") LocalDateTime paymentDueAt,
	@Schema(description = "내가 들어 있는 파티인지. 크루 홈에서 내 파티 카드만 상세로 보낸다") boolean myParty
) {
}
