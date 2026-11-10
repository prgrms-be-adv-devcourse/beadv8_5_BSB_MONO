package com.bukang.order.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.order.domain.PartyMember;
import com.bukang.order.domain.PartyStatus;

import jakarta.persistence.LockModeType;

public interface PartyMemberRepository extends JpaRepository<PartyMember, Long> {
	// 나간 줄도 찾는다. 다시 합류하면 새 줄 대신 이 줄을 되살린다 (uk_party_member)
	Optional<PartyMember> findByPartyIdAndMemberId(long partyId, long memberId);

	// 파티장이 떠날 때 넘겨받을 사람: 남은 파티원 중 가장 먼저 합류한 사람
	Optional<PartyMember> findFirstByPartyIdAndLeftAtIsNullAndMemberIdNotOrderByJoinedAtAscIdAsc(long partyId,
		long memberId);

	// 파티 조회 (BUK-39): 지금 들어 있는 파티원, 합류 순
	List<PartyMember> findByPartyIdInAndLeftAtIsNullOrderByJoinedAtAscIdAsc(Collection<Long> partyIds);

	// 내 파티 목록 (BUK-39)
	// shortcut: member_id로 시작하는 인덱스가 없다(uk_party_member는 party_id 먼저). 파티원 줄이 많아지면 인덱스를 더한다
	List<PartyMember> findByMemberIdAndLeftAtIsNull(long memberId);

	// 파티 신청 때 잠그기 전에 회원 서비스에 물을 파티원. 엔티티를 읽지 않으려고 회원 ID만 찾는다
	@Query("select m.memberId from PartyMember m where m.partyId = :partyId and m.leftAt is null")
	List<Long> findActiveMemberIds(@Param("partyId") long partyId);

	// 파티 줄을 잠근 뒤 지금 파티원을 잠가 읽는다 (합류 인원 확인, 파티 신청)
	// 잠금 읽기는 트랜잭션 처음 읽기 기준(MySQL REPEATABLE READ)이 아니라 커밋된 최신 줄을 본다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from PartyMember m where m.partyId = :partyId and m.leftAt is null")
	List<PartyMember> findActiveByPartyIdForUpdate(@Param("partyId") long partyId);

	// 지금 파티원 수 (파티장 포함)
	long countByPartyIdAndLeftAtIsNull(long partyId);

	// 이 크루에서 이 회원이 아직 들어 있는(나가지 않은) 파티 중 상태가 statuses인 것 (크루 나가기·내보내기 확인, BUK-78)
	// shortcut: member_id로 시작하는 인덱스가 없어 파티원 줄을 다 훑는다. 파티원 줄이 많아지면 (member_id) 인덱스를 더한다
	@Query("select new com.bukang.order.out.PartyMemberRepository$JoinedParty(p.id, p.status) "
		+ "from PartyMember m join Party p on p.id = m.partyId "
		+ "where p.crewId = :crewId and m.memberId = :memberId and m.leftAt is null and p.status in :statuses "
		+ "order by p.id")
	List<JoinedParty> findJoinedParties(@Param("crewId") long crewId, @Param("memberId") long memberId,
		@Param("statuses") Collection<PartyStatus> statuses);

	record JoinedParty(long partyId, PartyStatus status) {
	}
}
