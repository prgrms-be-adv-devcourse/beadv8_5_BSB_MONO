package com.bukang.member.crew.domain;

// 크루 역할. 선언 순서가 크루 홈의 정렬 순서다 (크루장 → 운영진 → 크루원)
public enum CrewRole {
	LEADER,
	MANAGER,
	MEMBER;

	// 초대 링크는 크루장과 운영진만 만든다
	public boolean canInvite() {
		return this != MEMBER;
	}
}
