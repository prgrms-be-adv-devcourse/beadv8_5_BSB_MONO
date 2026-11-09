package com.bukang.member.crew.out;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.member.crew.domain.CrewRemoval;

public interface CrewRemovalRepository extends JpaRepository<CrewRemoval, Long> {
	// 이 초대를 만든 뒤에(같은 시각 포함) 이 회원을 내보냈는지
	boolean existsByCrewIdAndMemberIdAndRemovedAtGreaterThanEqual(
		long crewId, long memberId, LocalDateTime inviteCreatedAt);
}
