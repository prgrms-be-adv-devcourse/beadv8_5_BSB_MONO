package com.bukang.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "비밀번호 재설정 메일 요청")
@AllArgsConstructor
@Getter
public class PasswordResetRequestDto {
	@Schema(description = "가입한 이메일", example = "runner1@crewrun.com")
	@NotBlank(message = "이메일을 입력해주세요.")
	@Email(message = "이메일 형식에 맞게 작성해주세요.")
	private final String email;
}
