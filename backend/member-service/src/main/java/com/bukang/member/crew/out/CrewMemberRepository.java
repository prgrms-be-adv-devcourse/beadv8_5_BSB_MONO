package com.bukang.member.crew.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.member.crew.domain.CrewMember;
import com.bukang.member.crew.domain.CrewRole;

public interface CrewMemberRepository extends JpaRepository<CrewMember, Long> {
	Optional<CrewMember> findByCrewIdAndMemberId(long crewId, long memberId);

	boolean existsByCrewIdAndMemberId(long crewId, long memberId);

	List<CrewMember> findAllByCrewId(long crewId);

	// 내 크루 목록 (idx_crew_member_member). 가입한 순서
	List<CrewMember> findAllByMemberIdOrderByJoinedAtAscIdAsc(long memberId);

	long countByCrewId(long crewId);

	// 크루장인 크루가 있는지 (회원 탈퇴 확인)
	boolean existsByMemberIdAndRole(long memberId, CrewRole role);

	// 크루마다 크루원 수 (모든 역할을 센다). 행은 [크루 ID, 수]
	@Query("select m.crewId, count(m) from CrewMember m where m.crewId in :crewIds group by m.crewId")
	List<Object[]> countByCrewIds(@Param("crewIds") Collection<Long> crewIds);

	// 회원 탈퇴: 그 회원의 모든 크루원 행을 지운다
	@Modifying(flushAutomatically = true)
	@Query("delete from CrewMember m where m.memberId = :memberId")
	int deleteAllByMemberId(@Param("memberId") long memberId);
}
