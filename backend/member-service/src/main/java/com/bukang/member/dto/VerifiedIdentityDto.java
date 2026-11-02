package com.bukang.member.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "본인인증 결과. 가입 화면은 이 값으로 이름 · 생년월일 · 휴대폰 번호를 채우고 잠근다")
public record VerifiedIdentityDto(
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
		description = "가입 요청에 넣는 인증 토큰(인증 ID와 같다). 15분 동안 쓸 수 있다",
		example = "crewrun-iv-6f1c2b9e-3a5d-4c7e-9b1a-2d8f0e4c6a71")
	String verificationToken,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
		description = "인증된 실명", example = "김러너")
	String name,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
		description = "인증된 생년월일", example = "1995-04-12")
	LocalDate birthDate,
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
		description = "인증된 휴대폰 번호", example = "010-1234-5678")
	String phone
) {
}
