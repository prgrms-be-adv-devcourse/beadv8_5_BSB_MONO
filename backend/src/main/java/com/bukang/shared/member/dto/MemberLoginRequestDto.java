package com.bukang.shared.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "로그인 요청")
@AllArgsConstructor
@Getter
public class MemberLoginRequestDto {
	@Schema(description = "이메일", example = "runner1@bukang.com")
	@NotBlank(message = "이메일을 입력해주세요.")
	private final String email;

	@Schema(description = "비밀번호", example = "password1234")
	@NotBlank(message = "비밀번호를 입력해주세요.")
	private final String password;
}
