package com.bukang.member.dto;

import java.time.LocalDate;

import com.bukang.member.domain.Gender;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "회원가입 요청")
@AllArgsConstructor
@Getter
public class MemberJoinRequestDto {
	@Schema(description = "이메일 (로그인 ID)", example = "runner1@crewrun.com")
	@NotBlank(message = "이메일을 작성해주세요.")
	@Email(message = "이메일 형식에 맞게 작성해주세요.")
	private final String email;

	// @Size는 null을 통과시키므로 @NotBlank를 함께 지정한다
	@Schema(description = "비밀번호 (10자 이상)", example = "password1234")
	@NotBlank(message = "비밀번호를 작성해주세요.")
	@Size(min = 10, message = "비밀번호는 10자 이상 작성해야합니다.")
	private final String password;

	@Schema(description = "실명", example = "김러너")
	@NotBlank(message = "이름을 작성해주세요.")
	private final String name;

	@Schema(description = "닉네임 (30자 이하)", example = "러너1")
	@NotBlank(message = "닉네임을 작성해주세요.")
	@Size(max = 30, message = "닉네임은 30자 이하로 작성해야합니다.")
	private final String nickname;

	// 010-1234-5678, 01012345678 형식 모두 허용
	@Schema(description = "휴대폰 번호 (하이픈 생략 가능)", example = "010-1234-5678")
	@NotBlank(message = "전화번호를 작성해주세요.")
	@Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$", message = "휴대폰 번호 형식에 맞게 작성해주세요.")
	private final String phone;

	// 만 14세 미만 검사는 MemberJoinUseCase에서 한다
	@Schema(description = "생년월일", example = "1995-04-12")
	@NotNull(message = "생년월일을 작성해주세요.")
	@Past(message = "생년월일은 오늘 이전 날짜여야 합니다.")
	private final LocalDate birthDate;

	@Schema(description = "성별 (선택)", example = "FEMALE", nullable = true)
	private final Gender gender;
}
