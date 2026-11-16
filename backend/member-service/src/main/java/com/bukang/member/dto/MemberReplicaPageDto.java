package com.bukang.member.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 회원 복사본 한 쪽 (내부 API, BUK-161). hasNext가 true면 nextAfterId를 afterId로 보내 다음 쪽을 받는다
 */
@Schema(description = "회원 복사본 목록 한 쪽 (서비스 간 내부 호출용)")
public record MemberReplicaPageDto(
	@Schema(description = "회원 ID 순서의 회원 목록")
	List<MemberReplicaDto> members,
	@Schema(description = "이 쪽 뒤에 받을 회원이 더 있으면 true", example = "true")
	boolean hasNext,
	@Schema(description = "다음 쪽을 받을 때 afterId로 보낼 값. 이 쪽 마지막 회원 ID이고, 목록이 비면 받은 afterId 그대로", example = "500")
	long nextAfterId
) {
}
