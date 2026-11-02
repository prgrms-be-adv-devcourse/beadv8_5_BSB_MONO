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

	// 아직 쓸 수 있는 초대: 쓰이지 않음, 취소 안 됨, 끝나는 시각이 지금보다 뒤
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

	// 가입에 쓴다. 바뀐 행이 1일 때만 가입한다. 같은 링크로 동시에 가입해도 한 명만 1을 받는다
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
		update CrewInvite i set i.usedAt = :now, i.usedBy = :memberId, i.updatedAt = :now
		where i.id = :inviteId and i.usedAt is null and i.canceledAt is null and i.expiresAt > :now
		""")
	int markUsed(
		@Param("inviteId") long inviteId, @Param("memberId") long memberId, @Param("now") LocalDateTime now);

	// 취소한다. 가입과 동시에 일어나도 둘 중 하나만 성공한다
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
		update CrewInvite i set i.canceledAt = :now, i.canceledBy = :memberId, i.updatedAt = :now
		where i.id = :inviteId and i.usedAt is null and i.canceledAt is null and i.expiresAt > :now
		""")
	int markCanceled(
		@Param("inviteId") long inviteId, @Param("memberId") long memberId, @Param("now") LocalDateTime now);
}
