package com.bukang.common.shared.crew.event;

/**
 * 크루원이 왜 크루에서 빠졌는지 (CrewMemberLeftEvent). 받는 쪽은 사유와 상관없이 그 크루의 파티에서 그 사람을 뺀다
 */
public enum CrewMemberLeftReason {
	// 크루원이 스스로 크루를 떠났다
	LEFT,
	// 크루장이 내보냈다
	REMOVED,
	// 크루장이 크루를 없앴다 (크루장 혼자 남았을 때만 된다)
	DISBANDED,
	// 회원 탈퇴로 모든 크루에서 빠졌다
	MEMBER_WITHDRAWN
}
