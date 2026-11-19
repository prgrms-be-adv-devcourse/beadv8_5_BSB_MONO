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

	// 내보내기·크루장 넘기기·없애기·이름 바꾸기, 남이 보낸 초대 취소는 크루장만 한다
	public boolean canManage() {
		return this == LEADER;
	}

	// 크루장은 크루를 나갈 수 없다. 크루장을 넘기거나 크루를 없앤다
	public boolean canLeave() {
		return this != LEADER;
	}

	// 초대 목록은 크루장이 모두, 운영진은 자기가 보낸 것만 본다
	public boolean seesAllInvites() {
		return this == LEADER;
	}
}
