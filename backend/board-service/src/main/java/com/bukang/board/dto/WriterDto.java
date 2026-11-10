package com.bukang.board.dto;

import com.bukang.board.domain.BoardMember;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "글쓴이 (회원 ID는 내보내지 않는다)")
public record WriterDto(
	@Schema(description = "닉네임. 탈퇴한 회원이면 「탈퇴한 회원」", example = "러너하나")
	String nickname,

	@Schema(description = "지금 판매자 승인 상태 (글을 쓴 때가 아니라 지금 상태). 탈퇴한 회원은 false", example = "false")
	boolean seller,

	@Schema(description = "탈퇴한 회원인지", example = "false")
	boolean withdrawn
) {
	// 회원 복사본이 아직 없으면(가입 이벤트가 늦음) 닉네임을 모른다
	private static final String UNKNOWN_NICKNAME = "알 수 없는 회원";

	public static WriterDto of(BoardMember member) {
		if (member == null) {
			return new WriterDto(UNKNOWN_NICKNAME, false, false);
		}
		if (member.isWithdrawn()) {
			return new WriterDto(BoardMember.WITHDRAWN_NICKNAME, false, true);
		}
		return new WriterDto(member.getNickname(), member.isSellerApproved(), false);
	}
}
