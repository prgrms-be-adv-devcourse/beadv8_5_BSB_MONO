package com.bukang.member.dto;

import com.bukang.member.domain.SocialProvider;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "소셜 가입 화면에 미리 채울 값")
public record SocialSignupInfoDto(
	@Schema(description = "소셜 로그인 제공자", example = "KAKAO")
	SocialProvider provider,
	@Schema(description = "제공자가 준 확인된 이메일. 없으면 null", example = "runner1@kakao.com", nullable = true)
	String email,
	@Schema(description = "true면 이메일을 고칠 수 없다(제공자가 확인한 이메일)", example = "true")
	boolean emailLocked,
	@Schema(description = "제공자가 준 이름. 없으면 null", nullable = true)
	String name
) {
}
