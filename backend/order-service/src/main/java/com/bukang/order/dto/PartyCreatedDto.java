package com.bukang.order.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 파티 만들기 응답: 파티, 파티장의 신청서, 초대 알림을 보낸 크루원
 */
public record PartyCreatedDto(
	@Schema(description = "파티") PartyDto party,
	@Schema(description = "파티장의 파티원 신청서 (결제 전)") ParticipationDto participation,
	@Schema(description = "초대 알림과 참가 링크를 보낸 회원 ID. 고른 사람 중 나 자신·크루원이 아닌 사람·"
		+ "파티 예매 시작 7일 전보다 늦게 가입한 사람은 빠진다", example = "[12, 15]") List<Long> invitedMemberIds
) {
}
