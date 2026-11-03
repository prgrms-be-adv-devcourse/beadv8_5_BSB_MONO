package com.bukang.member.dto;

import com.bukang.member.dto.validation.PasswordBytes;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "새 비밀번호 저장 요청")
@AllArgsConstructor
@Getter
public class PasswordResetConfirmDto {
	@Schema(description = "메일 링크 주소의 #token= 뒤 값", example = "q3Jx2y9bQe0Kk1m3Zp7u8VhR5sTgW4nA6cLdE2fGiHo")
	@NotBlank(message = "재설정 링크가 올바르지 않습니다.")
	private final String token;

	// 가입과 같은 규칙이다. BCrypt는 72바이트까지만 받으므로 바이트로도 센다 (BE-018)
	@Schema(description = "새 비밀번호 (10자 이상, UTF-8 72바이트 이하. 한글은 한 글자가 3바이트)", example = "newpassword1234")
	@NotBlank(message = "비밀번호를 작성해주세요.")
	@Size(min = 10, message = "비밀번호는 10자 이상 작성해야합니다.")
	@PasswordBytes
	private final String password;
}
