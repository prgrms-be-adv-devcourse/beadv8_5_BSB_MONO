package com.bukang.order.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.bukang.order.domain.ParticipationStatus;
import com.bukang.order.domain.PartyStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 파티 상세 (BUK-39, 시안 04·05). 파티원만 본다. 파티원 생년월일·연락처 같은 참가자 정보는 넣지 않는다
 * 참가 링크 토큰은 파티장에게만 준다 (11/03)
 */
public record PartyDetailDto(
	@Schema(description = "파티 ID", example = "1") long id,
	@Schema(description = "크루 ID", example = "7") long crewId,
	@Schema(description = "대회 ID", example = "100") long raceId,
	@Schema(description = "파티 종목 ID. 파티원 모두 이 종목으로 신청한다", example = "2") long raceCourseId,
	@Schema(description = "파티장 회원 ID", example = "3") long leaderMemberId,
	@Schema(description = "상태 RECRUITING(모집 중) / PAYING(결제 진행 중) / PAID(결제 끝) / CLOSED(종료)") PartyStatus status,
	@Schema(description = "파티원 결제 기한. 신청 전이면 null") LocalDateTime paymentDueAt,
	@Schema(description = "참가 링크 토큰. 파티장에게만 주고 파티원에게는 null", example = "k3Jx9...") String joinToken,
	@Schema(description = "참가 링크 만료 시각 (만든 뒤 24시간)") LocalDateTime joinTokenExpiresAt,
	@Schema(description = "파티원 수 (파티장 포함)", example = "3") int memberCount,
	@Schema(description = "결제를 마친(참가 확정) 파티원 수", example = "1") int paidCount,
	@Schema(description = "파티원 목록, 합류 순") List<Member> members
) {
	@Schema(name = "PartyDetailMemberDto", description = "파티 상세의 파티원 한 명")
	public record Member(
		@Schema(description = "회원 ID", example = "3") long memberId,
		@Schema(description = "닉네임. 회원 서비스에 없으면 「탈퇴한 회원」", example = "러너A") String nickname,
		@Schema(description = "파티장인지") boolean leader,
		@Schema(description = "합류 시각") LocalDateTime joinedAt,
		@Schema(description = "신청서 상태 PENDING(확보 전) / APPLIED(결제 대기) / CONFIRMED(참가 확정) / RELEASED(미결제 해제) "
			+ "/ FAILED(확보 실패) / CANCELED(취소)") ParticipationStatus participationStatus,
		@Schema(description = "결제했는지 (신청서가 참가 확정)") boolean paid
	) {
	}
}
