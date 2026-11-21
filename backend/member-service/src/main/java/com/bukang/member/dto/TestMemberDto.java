package com.bukang.member.dto;

import com.bukang.member.domain.Member;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "만든 테스트 회원")
public record TestMemberDto(
	@Schema(description = "회원 ID", example = "806")
	long memberId,
	@Schema(description = "이메일", example = "qa-buyer-1@crewrun.test")
	String email,
	@Schema(description = "닉네임", example = "QA구매자1")
	String nickname
) {
	public static TestMemberDto from(Member member) {
		return new TestMemberDto(member.getId(), member.getEmail(), member.getNickname());
	}
}
