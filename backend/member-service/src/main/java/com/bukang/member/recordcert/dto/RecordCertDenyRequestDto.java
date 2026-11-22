package com.bukang.member.recordcert.dto;

import com.bukang.member.recordcert.domain.DenyReason;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 승인 불가 요청 (BUK-276). 사유는 목록에서 하나, 메모는 선택
public record RecordCertDenyRequestDto(
	@Schema(description = "승인 불가 사유. NAME_MISMATCH(사진 속 이름이 다름), OTHER_RACE(다른 대회 기록), "
		+ "NOT_NET_TIME(넷타임 표시가 아님), BLURRY(사진이 흐림), OTHER(기타)",
		example = "NAME_MISMATCH", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull(message = "승인 불가 사유를 골라 주세요.")
	DenyReason reason,

	@Schema(description = "참가자에게 남기는 짧은 메모 (선택, 200자 이하). 공백만 있으면 남기지 않는다",
		example = "사진 속 이름이 참가자와 달라요")
	@Size(max = DenyReason.MEMO_MAX_LENGTH, message = "메모는 200자 이하로 써 주세요.")
	String memo
) {
}
