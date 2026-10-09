package com.bukang.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "회원가입 요청")
@AllArgsConstructor
@Getter
public class MemberJoinRequestDto {
	@Schema(description = "아이디", example = "runner1")
	@NotBlank(message = "아이디를 작성해주세요.")
	private final String username;

	// @Size는 null을 통과시키므로 @NotBlank를 함께 지정한다
	@Schema(description = "비밀번호 (10자 이상)", example = "password1234")
	@NotBlank(message = "비밀번호를 작성해주세요.")
	@Size(min = 10, message = "비밀번호는 10자 이상 작성해야합니다.")
	private final String password;

	@Schema(description = "닉네임", example = "러너1")
	@NotBlank(message = "닉네임을 작성해주세요.")
	private final String nickname;

	@Schema(description = "이메일 (로그인 ID)", example = "runner1@bukang.com")
	@NotBlank(message = "이메일을 작성해주세요.")
	@Email(message = "이메일 형식에 맞게 작성해주세요.")
	private final String email;

	// 010-1234-5678, 01012345678 형식 모두 허용
	@Schema(description = "휴대폰 번호 (하이픈 생략 가능)", example = "010-1234-5678")
	@NotBlank(message = "전화번호를 작성해주세요.")
	@Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$", message = "휴대폰 번호 형식에 맞게 작성해주세요.")
	private final String phone;
}
