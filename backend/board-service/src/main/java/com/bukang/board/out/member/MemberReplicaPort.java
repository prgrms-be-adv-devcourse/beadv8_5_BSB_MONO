package com.bukang.board.out.member;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 회원 서비스에서 회원 복사본 재료를 회원 ID 순서로 나눠 받는다 (GET /internal/v1/member/members/replicas, BUK-161)
 * 필드 이름은 회원 서비스의 MemberReplicaDto·MemberReplicaPageDto와 같아야 한다
 */
public interface MemberReplicaPort {
	// afterId보다 큰 회원을 size명까지 받는다. 실패하면 예외를 던진다
	MemberReplicaPage fetch(long afterId, int size);

	@JsonIgnoreProperties(ignoreUnknown = true)
	record MemberReplica(long memberId, String nickname, boolean sellerApproved, boolean withdrawn,
		LocalDateTime modifyDate) {
	}

	// hasNext가 true면 nextAfterId로 다음 쪽을 받는다
	@JsonIgnoreProperties(ignoreUnknown = true)
	record MemberReplicaPage(List<MemberReplica> members, boolean hasNext, long nextAfterId) {
	}
}
