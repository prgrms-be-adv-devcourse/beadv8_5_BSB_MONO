package com.bukang.order.dto;

import java.time.LocalDateTime;

import com.bukang.order.app.PartyUseCase.MyRaceParty;
import com.bukang.order.domain.Party;
import com.bukang.order.domain.PartyStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 이 대회에서 내 파티 상태 (BUK-91). 대회 상세 화면의 [파티로 신청] 버튼이 이것 하나로 문구를 고른다
 * 파티가 없으면 role=NONE, canApply=false, reason=NO_PARTY만 있고 나머지 칸은 빠진다
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PartyRaceStatusDto(
	@Schema(description = "이 대회 파티에서 내 역할 LEADER(파티장) / MEMBER(파티원) / NONE(파티 없음)", example = "LEADER")
	Role role,
	@Schema(description = "지금 파티로 신청할 수 있는지. 파티장이고, 모집 중이고, 인원이 최소~최대일 때만 true. 이때만 파티 대기열에 넣는다",
		example = "true")
	boolean canApply,
	@Schema(description = "신청할 수 없는 이유 (canApply=false일 때만) NO_PARTY(파티 없음) / NOT_LEADER(파티원이라 못 함) / "
		+ "NOT_RECRUITING(이미 신청함) / TOO_FEW_MEMBERS(최소 인원 모자람) / TOO_MANY_MEMBERS(최대 인원 넘음)",
		example = "TOO_FEW_MEMBERS")
	Reason reason,
	@Schema(description = "파티 ID", example = "1")
	Long partyId,
	@Schema(description = "파티 종목 ID", example = "2")
	Long raceCourseId,
	@Schema(description = "파티 상태 RECRUITING(모집 중) / PAYING(결제 진행 중) / PAID(결제 끝)", example = "RECRUITING")
	PartyStatus partyStatus,
	@Schema(description = "파티원 수 (파티장 포함)", example = "4")
	Integer memberCount,
	@Schema(description = "신청할 수 있는 최소 인원", example = "4")
	Integer minCount,
	@Schema(description = "파티 최대 인원", example = "10")
	Integer maxCount,
	@Schema(description = "결제를 마친(참가 확정) 파티원 수", example = "0")
	Integer paidCount,
	@Schema(description = "파티원 결제 마감 (파티 신청 뒤에만)", example = "2026-11-07T10:00:00")
	LocalDateTime paymentDueAt,
	@Schema(description = "내가 결제했는지 (내 신청서가 참가 확정)", example = "false")
	Boolean myPaid
) {
	public enum Role {
		LEADER, MEMBER, NONE
	}

	public enum Reason {
		NO_PARTY, NOT_LEADER, NOT_RECRUITING, TOO_FEW_MEMBERS, TOO_MANY_MEMBERS
	}

	public static PartyRaceStatusDto none() {
		return new PartyRaceStatusDto(Role.NONE, false, Reason.NO_PARTY, null, null, null, null, null, null, null,
			null, null);
	}

	public static PartyRaceStatusDto of(MyRaceParty mine, long memberId, int minCount, int maxCount) {
		Party party = mine.party();
		boolean leader = party.isLeader(memberId);
		Reason reason = reason(leader, party.getStatus(), mine.memberCount(), minCount, maxCount);
		return new PartyRaceStatusDto(leader ? Role.LEADER : Role.MEMBER, reason == null, reason, party.getId(),
			party.getRaceCourseId(), party.getStatus(), mine.memberCount(), minCount, maxCount, mine.paidCount(),
			party.getPaymentDueAt(), mine.myPaid());
	}

	private static Reason reason(boolean leader, PartyStatus status, int memberCount, int minCount, int maxCount) {
		if (!leader) {
			return Reason.NOT_LEADER;
		}
		if (status != PartyStatus.RECRUITING) {
			return Reason.NOT_RECRUITING;
		}
		if (memberCount < minCount) {
			return Reason.TOO_FEW_MEMBERS;
		}
		return memberCount > maxCount ? Reason.TOO_MANY_MEMBERS : null;
	}
}
