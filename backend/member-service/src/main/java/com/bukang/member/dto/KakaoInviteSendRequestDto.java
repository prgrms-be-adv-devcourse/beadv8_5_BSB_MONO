package com.bukang.member.dto;

import java.util.HashSet;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "크루 초대 카카오톡 보내기 요청")
public record KakaoInviteSendRequestDto(
	@Schema(description = "받는 사람의 카카오 uuid (친구 조회 응답의 uuid). 1~5명, 중복 없이",
		example = "[\"abcdefg0001\", \"abcdefg0002\"]")
	@NotNull
	@Size(min = 1, max = 5)
	// 카카오에 보낼 JSON 배열에 그대로 넣으므로 따옴표·역슬래시가 없는 값만 받는다
	List<@NotNull @Pattern(regexp = "[A-Za-z0-9_\\-+/=]{1,100}") String> receiverUuids
) {
	@Schema(hidden = true)
	@AssertTrue(message = "받는 사람이 중복되었습니다.")
	public boolean isReceiverUuidsDistinct() {
		return receiverUuids == null || new HashSet<>(receiverUuids).size() == receiverUuids.size();
	}
}
