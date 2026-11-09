package com.bukang.member.crew.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.member.crew.domain.CrewInvite;

import jakarta.persistence.LockModeType;

public interface CrewInviteRepository extends JpaRepository<CrewInvite, Long> {
	// 토큰 원문이 아니라 SHA-256 값으로 찾는다
	Optional<CrewInvite> findByTokenHash(String tokenHash);

	Optional<CrewInvite> findByIdAndCrewId(long id, long crewId);

	// 조건부 UPDATE가 0행일 때 이유를 고르려고 다시 읽는다. MySQL 기본 격리 수준(REPEATABLE READ)에서
	// 일반 SELECT는 트랜잭션 처음 읽은 시점의 값을 보므로, 잠금 읽기로 다른 요청이 커밋한 최신 값을 본다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from CrewInvite i where i.id = :id")
	Optional<CrewInvite> findByIdForUpdate(@Param("id") long id);

	// 아직 쓸 수 있는 초대: 취소 안 됨, 끝나는 시각이 지금보다 뒤
	// usedAt은 여러 명 가입(BUK-135) 전에 한 번 쓰고 닫힌 옛 초대에만 있다
	@Query("""
		select i from CrewInvite i
		where i.crewId = :crewId and i.usedAt is null and i.canceledAt is null and i.expiresAt > :now
		order by i.createdAt desc, i.id desc
		""")
	List<CrewInvite> findUsableByCrewId(@Param("crewId") long crewId, @Param("now") LocalDateTime now);

	@Query("""
		select i from CrewInvite i
		where i.crewId = :crewId and i.createdBy = :createdBy
			and i.usedAt is null and i.canceledAt is null and i.expiresAt > :now
		order by i.createdAt desc, i.id desc
		""")
	List<CrewInvite> findUsableByCrewIdAndCreatedBy(
		@Param("crewId") long crewId, @Param("createdBy") long createdBy, @Param("now") LocalDateTime now);

	// 취소한다. 가입은 초대 행을 잠가 읽으므로 취소가 커밋된 뒤의 가입은 막힌다
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
		update CrewInvite i set i.canceledAt = :now, i.canceledBy = :memberId, i.updatedAt = :now
		where i.id = :inviteId and i.usedAt is null and i.canceledAt is null and i.expiresAt > :now
		""")
	int markCanceled(
		@Param("inviteId") long inviteId, @Param("memberId") long memberId, @Param("now") LocalDateTime now);

	// 크루 없애기: 그 크루의 모든 초대 행을 지운다
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from CrewInvite i where i.crewId = :crewId")
	int deleteAllByCrewId(@Param("crewId") long crewId);
}
