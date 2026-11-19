package com.bukang.member.crew.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

// 크루원 (CrewMember)
class CrewMemberTest {
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 10, 0);

	@Test
	@DisplayName("크루장 역할인 크루원만 isLeader가 true다")
	void isLeader() {
		assertThat(CrewMember.leader(1L, 10L, NOW).isLeader()).isTrue();
		assertThat(CrewMember.invited(1L, 11L, 5L, NOW).isLeader()).isFalse();

		CrewMember manager = CrewMember.invited(1L, 12L, 5L, NOW);
		manager.changeRole(CrewRole.MANAGER, NOW);
		assertThat(manager.isLeader()).isFalse();
	}

	@Test
	@DisplayName("크루 홈 정렬은 크루장 → 운영진 → 크루원, 같은 역할은 먼저 가입한 순서, 가입 시각이 같으면 ID 순서다")
	void homeOrder() {
		CrewMember lateMember = member(1, CrewRole.MEMBER, NOW.plusHours(2));
		CrewMember earlyMember = member(2, CrewRole.MEMBER, NOW.plusHours(1));
		CrewMember sameTimeLowerId = member(3, CrewRole.MEMBER, NOW.plusHours(1));
		CrewMember manager = member(4, CrewRole.MANAGER, NOW.plusHours(3));
		CrewMember leader = member(5, CrewRole.LEADER, NOW.plusHours(4));

		List<CrewMember> sorted = List.of(lateMember, earlyMember, sameTimeLowerId, manager, leader).stream()
			.sorted(CrewMember.HOME_ORDER)
			.toList();

		assertThat(sorted).containsExactly(leader, manager, earlyMember, sameTimeLowerId, lateMember);
	}

	private CrewMember member(long id, CrewRole role, LocalDateTime joinedAt) {
		CrewMember member = CrewMember.invited(1L, id, 5L, joinedAt);
		member.changeRole(role, joinedAt);
		ReflectionTestUtils.setField(member, "id", id);
		return member;
	}
}
