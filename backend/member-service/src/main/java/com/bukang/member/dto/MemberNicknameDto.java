package com.bukang.member.dto;

import com.bukang.member.domain.Member;
import com.bukang.member.domain.MemberStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 회원 ID와 닉네임 (내부 API). 다른 서비스가 회원 ID만 갖고 있을 때 화면에 띄울 이름을 받아 간다
 * 닉네임 말고 다른 개인정보(이름, 이메일, 휴대폰 번호, 생년월일)는 여기에 넣지 않는다
 */
@Schema(description = "회원 닉네임 (서비스 간 내부 호출용)")
public record MemberNicknameDto(
	@Schema(description = "회원 ID", example = "1")
	long memberId,
	@Schema(description = "닉네임. 탈퇴한 회원이면 「탈퇴한 회원」", example = "러너A")
	String nickname
) {
	// 조회 쿼리(MemberRepository.findNicknamesByIdIn)가 부른다
	// 탈퇴하면 닉네임이 지워지므로, 다른 서비스 사본과 같은 이름으로 보여 준다
	public MemberNicknameDto(Long memberId, String nickname, MemberStatus status) {
		this(memberId, status == MemberStatus.WITHDRAWN ? Member.WITHDRAWN_NICKNAME : nickname);
	}
}
