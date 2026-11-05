package com.bukang.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

// 토큰은 경로가 아니라 본문으로 받는다. 경로에 두면 접속 로그에 링크가 그대로 남는다
@Schema(description = "참가 링크로 파티에 합류하는 요청. 종목은 고르지 않고 파티 종목으로 신청한다")
@AllArgsConstructor
@Getter
public class PartyJoinRequestDto {
	@Schema(description = "참가 링크 토큰", example = "k3Jx9...")
	@NotBlank(message = "참가 링크가 없습니다.")
	@Size(max = 64, message = "참가 링크가 올바르지 않습니다.")
	private final String joinToken;

	@Schema(description = "참가자 정보")
	@NotNull(message = "참가자 정보를 작성해주세요.")
	@Valid
	private final ParticipantInfoRequestDto participant;

	@Schema(description = "개인정보 제3자 제공 동의 (필수, true만 받는다)", example = "true")
	@NotNull(message = "개인정보 제3자 제공에 동의해주세요.")
	@AssertTrue(message = "개인정보 제3자 제공에 동의해야 신청할 수 있습니다.")
	private final Boolean thirdPartyConsent;
}
