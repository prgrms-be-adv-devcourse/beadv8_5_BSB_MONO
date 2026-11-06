package com.bukang.order.dto;

import java.time.LocalDateTime;

import com.bukang.order.domain.Party;
import com.bukang.order.domain.PartyStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 파티장에게 주는 파티 정보. 참가 링크 토큰은 파티장 응답에만 넣는다 (11/03, 로그에 남기지 않는다)
 */
public record PartyDto(
	@Schema(description = "파티 ID", example = "1") long id,
	@Schema(description = "크루 ID", example = "7") long crewId,
	@Schema(description = "대회 ID", example = "100") long raceId,
	@Schema(description = "파티 종목 ID. 파티원 모두 이 종목으로 신청한다", example = "2") long raceCourseId,
	@Schema(description = "파티장 회원 ID", example = "3") long leaderMemberId,
	@Schema(description = "상태 RECRUITING(모집 중) / PAYING(결제 진행 중) / PAID(결제 끝) / CLOSED(종료)") PartyStatus status,
	@Schema(description = "파티원 결제 기한. 신청 전이면 null") LocalDateTime paymentDueAt,
	@Schema(description = "참가 링크 토큰. 프론트가 링크 주소에 붙인다", example = "k3Jx9...") String joinToken,
	@Schema(description = "참가 링크 만료 시각 (만든 뒤 24시간)") LocalDateTime joinTokenExpiresAt
) {
	public static PartyDto of(Party party) {
		return new PartyDto(party.getId(), party.getCrewId(), party.getRaceId(), party.getRaceCourseId(),
			party.getLeaderMemberId(), party.getStatus(), party.getPaymentDueAt(), party.getJoinToken(),
			party.getJoinTokenExpiresAt());
	}
}
