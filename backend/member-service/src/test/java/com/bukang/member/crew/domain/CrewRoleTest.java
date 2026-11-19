package com.bukang.member.crew.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 역할별 권한 (CrewRole)
class CrewRoleTest {
	@Test
	@DisplayName("초대 링크는 크루장과 운영진만 만든다")
	void canInvite() {
		assertThat(CrewRole.LEADER.canInvite()).isTrue();
		assertThat(CrewRole.MANAGER.canInvite()).isTrue();
		assertThat(CrewRole.MEMBER.canInvite()).isFalse();
	}

	@Test
	@DisplayName("크루 관리(내보내기·크루장 넘기기·없애기·이름 바꾸기)는 크루장만 한다")
	void canManage() {
		assertThat(CrewRole.LEADER.canManage()).isTrue();
		assertThat(CrewRole.MANAGER.canManage()).isFalse();
		assertThat(CrewRole.MEMBER.canManage()).isFalse();
	}

	@Test
	@DisplayName("크루장은 크루를 나갈 수 없고 운영진·크루원은 나갈 수 있다")
	void canLeave() {
		assertThat(CrewRole.LEADER.canLeave()).isFalse();
		assertThat(CrewRole.MANAGER.canLeave()).isTrue();
		assertThat(CrewRole.MEMBER.canLeave()).isTrue();
	}

	@Test
	@DisplayName("초대 목록은 크루장만 모두 보고 운영진은 자기가 보낸 것만 본다")
	void seesAllInvites() {
		assertThat(CrewRole.LEADER.seesAllInvites()).isTrue();
		assertThat(CrewRole.MANAGER.seesAllInvites()).isFalse();
		assertThat(CrewRole.MEMBER.seesAllInvites()).isFalse();
	}
}
