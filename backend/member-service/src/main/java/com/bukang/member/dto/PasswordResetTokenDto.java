package com.bukang.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "비밀번호 재설정 링크 확인 요청")
@AllArgsConstructor
@Getter
public class PasswordResetTokenDto {
	@Schema(description = "메일 링크 주소의 #token= 뒤 값", example = "q3Jx2y9bQe0Kk1m3Zp7u8VhR5sTgW4nA6cLdE2fGiHo")
	@NotBlank(message = "재설정 링크가 올바르지 않습니다.")
	private final String token;
}
