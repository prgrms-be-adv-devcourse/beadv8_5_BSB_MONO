package com.bukang.order.app;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.order.domain.PartyStatus;
import com.bukang.order.dto.CrewMemberPartyStatusDto;
import com.bukang.order.dto.CrewMemberPartyStatusDto.BlockingParty;
import com.bukang.order.dto.CrewPartyStatusDto;
import com.bukang.order.out.PartyMemberRepository;
import com.bukang.order.out.PartyRepository;

import lombok.RequiredArgsConstructor;

/**
 * 회원 서비스가 크루 나가기·내보내기·없애기 전에 부르는 파티 상태 조회 (11/05 회의 C1·C2, BUK-78·BUK-125). 조회만 한다
 * 나가기(C1): 끝나지 않은 파티(모집 중·결제 진행 중·결제 끝)에 들어 있으면 막는다
 * 내보내기(C2): 결제 진행 중·결제 끝 파티에 들어 있으면 막는다. 모집 중이면 내보낼 수 있다
 * 없애기: 그 크루에 끝나지 않은 파티가 하나라도 있으면 막는다
 * 결제 끝 파티도 대회 다음 날 종료될 때까지 막는다 (11/09 결정, #340 추천값을 뒤집음). 종료는 PartyCloseUseCase
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CrewMemberPartyQueryUseCase {
	private static final List<PartyStatus> UNFINISHED = List.of(PartyStatus.RECRUITING, PartyStatus.PAYING,
		PartyStatus.PAID);

	private final PartyMemberRepository partyMemberRepository;
	private final PartyRepository partyRepository;

	public CrewMemberPartyStatusDto findPartyStatus(long crewId, long memberId) {
		List<BlockingParty> parties = partyMemberRepository.findJoinedParties(crewId, memberId, UNFINISHED).stream()
			.map(party -> new BlockingParty(party.partyId(), party.status()))
			.toList();
		boolean applied = parties.stream().anyMatch(party -> party.status() != PartyStatus.RECRUITING);
		return new CrewMemberPartyStatusDto(parties.isEmpty(), !applied, parties);
	}

	public CrewPartyStatusDto findCrewPartyStatus(long crewId) {
		List<BlockingParty> parties = partyRepository.findByCrewIdAndStatusInOrderByIdAsc(crewId, UNFINISHED).stream()
			.map(party -> new BlockingParty(party.getId(), party.getStatus()))
			.toList();
		return new CrewPartyStatusDto(parties.isEmpty(), parties);
	}
}
