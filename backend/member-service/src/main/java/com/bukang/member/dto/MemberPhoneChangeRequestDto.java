package com.bukang.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

// 휴대폰 번호는 화면이 보낸 번호가 아니라 서버가 확인한 본인인증 결과의 번호로 바꾼다 (BUK-71)
@Schema(description = "휴대폰 번호 바꾸기 요청")
public record MemberPhoneChangeRequestDto(
	@Schema(description = "회원 전용 본인인증 확인에서 받은 인증 토큰",
		example = "crewrun-iv-6f1c2b9e-3a5d-4c7e-9b1a-2d8f0e4c6a71")
	@NotBlank(message = "본인인증을 다시 해주세요.")
	String verificationToken
) {
}
