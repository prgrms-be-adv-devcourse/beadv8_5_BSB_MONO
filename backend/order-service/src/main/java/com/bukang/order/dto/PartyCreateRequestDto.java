package com.bukang.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "파티 만들기 요청. 파티장도 참가자라 자기 종목과 참가자 정보를 함께 보낸다")
@AllArgsConstructor
@Getter
public class PartyCreateRequestDto {
	@Schema(description = "파티를 만들 크루 ID", example = "7")
	@NotNull(message = "크루를 선택해주세요.")
	@Positive(message = "크루 ID는 0보다 커야 합니다.")
	private final Long crewId;

	@Schema(description = "파티장이 뛸 종목 ID. 파티는 이 종목의 대회 단위다", example = "1")
	@NotNull(message = "종목을 선택해주세요.")
	@Positive(message = "종목 ID는 0보다 커야 합니다.")
	private final Long raceCourseId;

	@Schema(description = "참가자 정보")
	@NotNull(message = "참가자 정보를 작성해주세요.")
	@Valid
	private final ParticipantInfoRequestDto participant;

	@Schema(description = "개인정보 제3자 제공 동의 (필수, true만 받는다)", example = "true")
	@NotNull(message = "개인정보 제3자 제공에 동의해주세요.")
	@AssertTrue(message = "개인정보 제3자 제공에 동의해야 신청할 수 있습니다.")
	private final Boolean thirdPartyConsent;
}
