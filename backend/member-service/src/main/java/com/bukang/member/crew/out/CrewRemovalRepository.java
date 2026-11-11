package com.bukang.member.crew.out;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.member.crew.domain.CrewRemoval;

public interface CrewRemovalRepository extends JpaRepository<CrewRemoval, Long> {
	// 이 초대를 만든 뒤에(같은 시각 포함) 이 회원을 내보냈는지
	boolean existsByCrewIdAndMemberIdAndRemovedAtGreaterThanEqual(
		long crewId, long memberId, LocalDateTime inviteCreatedAt);

	// 크루 없애기: 그 크루의 내보낸 기록을 지운다
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from CrewRemoval r where r.crewId = :crewId")
	int deleteAllByCrewId(@Param("crewId") long crewId);
}
