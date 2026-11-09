package com.bukang.order.app;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.order.domain.PartyStatus;
import com.bukang.order.dto.CrewMemberPartyStatusDto;
import com.bukang.order.dto.CrewMemberPartyStatusDto.BlockingParty;
import com.bukang.order.out.PartyMemberRepository;

import lombok.RequiredArgsConstructor;

/**
 * 회원 서비스가 크루 나가기·내보내기 전에 부르는 파티 상태 조회 (11/05 회의 C1·C2, BUK-78). 조회만 한다
 * 나가기(C1): 끝나지 않은 파티(모집 중·결제 진행 중)에 들어 있으면 막는다
 * 내보내기(C2): 결제 진행 중인 파티에 들어 있으면 결제했든 안 했든 막는다. 모집 중이면 내보낼 수 있다
 * 결제 끝(PAID) 파티는 막지 않는다. 파티원이 모두 참가권을 받아 크루와 상관없이 대회에 나갈 수 있다
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CrewMemberPartyQueryUseCase {
	private static final List<PartyStatus> UNFINISHED = List.of(PartyStatus.RECRUITING, PartyStatus.PAYING);

	private final PartyMemberRepository partyMemberRepository;

	public CrewMemberPartyStatusDto findPartyStatus(long crewId, long memberId) {
		List<BlockingParty> parties = partyMemberRepository.findJoinedParties(crewId, memberId, UNFINISHED).stream()
			.map(party -> new BlockingParty(party.partyId(), party.status()))
			.toList();
		boolean paying = parties.stream().anyMatch(party -> party.status() == PartyStatus.PAYING);
		return new CrewMemberPartyStatusDto(parties.isEmpty(), !paying, parties);
	}
}
