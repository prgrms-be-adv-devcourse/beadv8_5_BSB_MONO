package com.bukang.member.dto;

import com.bukang.member.dto.validation.NicknameWidth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;

// 가입 뒤에는 닉네임만 바꾼다. 이름 · 생년월일 · 휴대폰 번호는 본인인증으로 받은 값이라 바꿀 수 없다 (BUK-33)
// 요청에 그 칸들이 와도 모르는 칸으로 무시한다. 닉네임 검사 규칙은 가입(MemberJoinRequestDto)과 같다
@Schema(description = "내 정보 수정 요청")
@AllArgsConstructor
@Getter
public class MemberModifyRequestDto {
	// 칸 수는 한글 2칸, 영문·숫자 1칸으로 센다 (BE-013)
	@Schema(description = "닉네임 (한글 2~8자, 영문 4~16자, 한글·영문·숫자만)", example = "러너1")
	@NotBlank(message = "닉네임을 작성해주세요.")
	@Pattern(regexp = "^[가-힣A-Za-z0-9]+$", message = "닉네임은 한글, 영문, 숫자만 쓸 수 있습니다.")
	@NicknameWidth
	private final String nickname;
}
