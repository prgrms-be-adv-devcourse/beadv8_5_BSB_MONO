package com.bukang.member.crew.out;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.member.crew.domain.CrewPenalty;

public interface CrewPenaltyRepository extends JpaRepository<CrewPenalty, Long> {
	boolean existsByCrewIdAndPenaltyMonth(long crewId, String penaltyMonth);

	// 지금 제한 중인 행 가운데 가장 늦게 끝나는 시각. 제한 중이 아니면 null
	@Query("select max(p.endAt) from CrewPenalty p where p.crewId = :crewId and p.startAt <= :now and p.endAt > :now")
	LocalDateTime findRestrictedUntil(@Param("crewId") long crewId, @Param("now") LocalDateTime now);
}
