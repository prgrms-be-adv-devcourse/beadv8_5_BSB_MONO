package com.bukang.member.dto;

import java.util.List;

import com.bukang.member.domain.Gender;
import com.bukang.member.domain.TermsType;
import com.bukang.member.dto.validation.NicknameWidth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

// 이메일 가입 요청(MemberJoinRequestDto)에서 비밀번호를 빼고 이메일을 선택으로 바꿨다. 나머지 검사는 같다 (BE-033)
@Schema(description = "소셜 회원가입 요청")
@AllArgsConstructor
@Getter
public class SocialJoinRequestDto {
	// 제공자가 확인된 이메일을 줬으면 그 값을 쓰고 이 값은 무시한다. 안 줬을 때만 필수다 (BE-033, SocialJoinUseCase)
	@Schema(description = "이메일 (255자 이하). 제공자가 확인된 이메일을 줬으면 무시하고, 안 줬으면 필수",
		example = "runner1@crewrun.com", nullable = true)
	@Email(message = "이메일 형식에 맞게 작성해주세요.")
	@Size(max = 255, message = "이메일은 255자 이하로 작성해주세요.")
	private final String email;

	// 칸 수는 한글 2칸, 영문·숫자 1칸으로 센다 (BE-013)
	@Schema(description = "닉네임 (한글 2~8자, 영문 4~16자, 한글·영문·숫자만)", example = "러너1")
	@NotBlank(message = "닉네임을 작성해주세요.")
	@Pattern(regexp = "^[가-힣A-Za-z0-9]+$", message = "닉네임은 한글, 영문, 숫자만 쓸 수 있습니다.")
	@NicknameWidth
	private final String nickname;

	@Schema(description = "성별 (선택)", example = "FEMALE", nullable = true)
	private final Gender gender;

	// 동의한 약관 종류. 필수 약관이 빠졌는지는 MemberJoinUseCase에서 확인한다 (BE-021)
	// 모르는 종류가 오면 JSON을 읽다 실패해 400 INVALID_REQUEST가 된다
	@Schema(description = "동의한 약관 종류 (SERVICE, PRIVACY는 필수). 동의하지 않은 선택 약관은 빼고 보낸다",
		example = "[\"SERVICE\", \"PRIVACY\", \"MARKETING\"]")
	@NotNull(message = "동의한 약관 목록을 작성해주세요.")
	private final List<TermsType> agreedTerms;

	// 본인인증(포트원)으로 받은 토큰. 이름 · 생년월일 · 휴대폰 번호는 이 토큰으로 서버가 확인한 값을 쓴다 (BUK-33)
	@Schema(description = "본인인증 결과의 인증 토큰. 이름 · 생년월일 · 휴대폰 번호는 이 인증으로 확인한 값으로 가입한다",
		example = "crewrun-iv-6f1c2b9e-3a5d-4c7e-9b1a-2d8f0e4c6a71")
	@NotBlank(message = "본인인증을 해주세요.")
	private final String verificationToken;
}
