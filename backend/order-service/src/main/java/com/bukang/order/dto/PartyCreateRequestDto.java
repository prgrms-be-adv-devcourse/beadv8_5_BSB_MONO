package com.bukang.order.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Schema(description = "파티 만들기 요청. 파티장도 참가자라 파티 종목과 자기 참가자 정보를 함께 보낸다")
@AllArgsConstructor
@Getter
public class PartyCreateRequestDto {
	@Schema(description = "파티를 만들 크루 ID", example = "7")
	@NotNull(message = "크루를 선택해주세요.")
	@Positive(message = "크루 ID는 0보다 커야 합니다.")
	private final Long crewId;

	@Schema(description = "파티 종목 ID. 파티원 모두 이 종목으로 신청하고, 만든 뒤에는 바꿀 수 없다", example = "1")
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

	// 50명은 추천값(미확정): 판매자 최대 인원이 비어 있을 수 있어 위쪽 한도만 둔다
	@Schema(description = "초대할 크루원 회원 ID (선택). 파티를 만든 뒤 이 사람들에게 초대 알림과 참가 링크를 보낸다. "
		+ "비워도 파티는 만들어진다. 나 자신·같은 사람 두 번·크루원이 아닌 사람·파티 예매 시작 7일 전보다 늦게 가입한 사람은 "
		+ "거절하지 않고 건너뛴다 (응답 invitedMemberIds에서 빠짐)", example = "[12, 15]")
	@Size(max = 50, message = "한 번에 50명까지 초대할 수 있습니다.")
	private final List<@NotNull(message = "초대할 회원 ID를 확인해주세요.")
		@Positive(message = "회원 ID는 0보다 커야 합니다.") Long> invitedMemberIds;
}
