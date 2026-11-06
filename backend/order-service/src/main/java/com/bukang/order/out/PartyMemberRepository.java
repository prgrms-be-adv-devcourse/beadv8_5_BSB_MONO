package com.bukang.order.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bukang.order.domain.PartyMember;
import com.bukang.order.domain.PartyStatus;

public interface PartyMemberRepository extends JpaRepository<PartyMember, Long> {
	// 나간 줄도 찾는다. 다시 합류하면 새 줄 대신 이 줄을 되살린다 (uk_party_member)
	Optional<PartyMember> findByPartyIdAndMemberId(long partyId, long memberId);

	// 파티장이 떠날 때 넘겨받을 사람: 남은 파티원 중 가장 먼저 합류한 사람
	Optional<PartyMember> findFirstByPartyIdAndLeftAtIsNullAndMemberIdNotOrderByJoinedAtAscIdAsc(long partyId,
		long memberId);

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
