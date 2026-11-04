package com.bukang.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "본인인증 시작 응답")
public record IdentityVerificationStartDto(
	@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
		description = "포트원 SDK requestIdentityVerification()에 넘길 인증 ID. 15분 안에 인증을 마쳐야 한다",
		example = "crewrun-iv-6f1c2b9e-3a5d-4c7e-9b1a-2d8f0e4c6a71")
	String identityVerificationId
) {
}
