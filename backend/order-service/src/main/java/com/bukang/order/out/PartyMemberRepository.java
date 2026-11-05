package com.bukang.order.out;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.order.domain.PartyMember;

public interface PartyMemberRepository extends JpaRepository<PartyMember, Long> {
	// 나간 줄도 찾는다. 다시 합류하면 새 줄 대신 이 줄을 되살린다 (uk_party_member)
	Optional<PartyMember> findByPartyIdAndMemberId(long partyId, long memberId);

	// 파티장이 떠날 때 넘겨받을 사람: 남은 파티원 중 가장 먼저 합류한 사람
	Optional<PartyMember> findFirstByPartyIdAndLeftAtIsNullAndMemberIdNotOrderByJoinedAtAscIdAsc(long partyId,
		long memberId);
}
