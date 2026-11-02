package com.bukang.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "참가 신청서 작성 요청")
@AllArgsConstructor
@Getter
public class ParticipationApplyRequestDto {
	@Schema(description = "신청할 종목(참가권) ID", example = "1")
	@NotNull(message = "종목을 선택해주세요.")
	@Positive(message = "종목 ID는 0보다 커야 합니다.")
	private final Long raceCourseId;

	@Schema(description = "참가자 정보")
	@NotNull(message = "참가자 정보를 작성해주세요.")
	@Valid
	private final ParticipantInfoRequestDto participant;

	// 참가자 정보가 판매자(주최자)에게 넘어가므로 필수다. 거부하면 신청할 수 없다 (정책 3장 1-3)
	@Schema(description = "개인정보 제3자 제공 동의 (필수, true만 받는다)", example = "true")
	@NotNull(message = "개인정보 제3자 제공에 동의해주세요.")
	@AssertTrue(message = "개인정보 제3자 제공에 동의해야 신청할 수 있습니다.")
	private final Boolean thirdPartyConsent;
}
