package com.bukang.order.out;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.order.domain.Party;

import jakarta.persistence.LockModeType;

public interface PartyRepository extends JpaRepository<Party, Long> {
	// 참가 링크로 파티 ID만 찾는다. 잠그기 전에 엔티티를 읽지 않으려고 ID만 돌려준다 (uk_party_join_token)
	@Query("select p.id from Party p where p.joinToken = :joinToken")
	Optional<Long> findIdByJoinToken(@Param("joinToken") String joinToken);

	// 합류·나가기·링크 다시 만들기·(다음 이슈) 신청은 파티 줄을 먼저 잠근다 (잠금 순서: 파티 → 참가 신청)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Party p where p.id = :id")
	Optional<Party> findByIdForUpdate(@Param("id") long id);
}
