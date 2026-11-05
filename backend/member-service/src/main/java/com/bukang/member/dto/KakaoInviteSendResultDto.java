package com.bukang.member.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "크루 초대 카카오톡 보내기 결과. 한 명이라도 보냈을 때 200으로 온다")
public record KakaoInviteSendResultDto(
	@Schema(description = "보낸 받는 사람의 uuid")
	List<String> sentUuids,
	@Schema(description = "보내지 못한 받는 사람과 사유. 이 사람 몫의 초대 링크는 취소했다")
	List<Failure> failures
) {
	@Schema(description = "보내지 못한 받는 사람")
	public record Failure(
		@Schema(example = "abcdefg0002") String uuid,
		@Schema(description = "DAILY_LIMIT: 오늘 보낼 수 있는 양을 다 씀, KAKAO_ERROR: 그 밖의 카카오 실패")
		Reason reason
	) {
	}

	public enum Reason {
		DAILY_LIMIT,
		KAKAO_ERROR
	}
}
