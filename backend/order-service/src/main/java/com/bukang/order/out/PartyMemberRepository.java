package com.bukang.order.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bukang.order.domain.PartyMember;

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

	// 지금 파티원 수 (파티장 포함)
	long countByPartyIdAndLeftAtIsNull(long partyId);
}
