package com.bukang.member.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "이름과 생년월일이 같고 휴대폰이 다른 회원 묶음")
public record IdentityReviewGroupDto(
	@Schema(description = "묶음의 회원 (최근 가입자부터)")
	List<IdentityReviewMemberDto> members
) {
}
