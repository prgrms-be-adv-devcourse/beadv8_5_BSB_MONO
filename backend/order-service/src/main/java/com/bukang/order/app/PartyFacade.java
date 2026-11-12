package com.bukang.order.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.order.dto.ParticipationDto;
import com.bukang.order.dto.PartyCreateRequestDto;
import com.bukang.order.dto.PartyCreatedDto;
import com.bukang.order.dto.PartyDto;
import com.bukang.order.dto.PartyJoinRequestDto;
import com.bukang.order.dto.PartyRaceStatusDto;
import com.bukang.order.dto.PartyRaceStatusDto.MyParticipation;
import com.bukang.order.out.ParticipationRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class PartyFacade {
	private final PartyUseCase partyUseCase;
	private final PartyApplyUseCase partyApplyUseCase;
	private final ParticipationRepository participationRepository;

	public PartyCreatedDto create(PartyCreateRequestDto request, long memberId) {
		PartyUseCase.Created created = partyUseCase.create(memberId, request.getCrewId(), request.getRaceCourseId(),
			request.getParticipant().toParticipantInfo(), LocalDateTime.now());
		return new PartyCreatedDto(PartyDto.of(created.party()), created.participation().toDto());
	}

	public PartyDto regenerateJoinLink(long partyId, long memberId) {
		return PartyDto.of(partyUseCase.regenerateJoinLink(partyId, memberId, LocalDateTime.now()));
	}

	public ParticipationDto join(PartyJoinRequestDto request, long memberId) {
		return partyUseCase.join(request.getJoinToken(), memberId,
			request.getParticipant().toParticipantInfo(), LocalDateTime.now()).toDto();
	}

	// 상품에 크루 자리를 요청하기 전에 확인을 커밋해야 해서 트랜잭션을 PartyApplyUseCase가 나눠 연다 (BUK-105)
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public PartyDto apply(long partyId, long memberId) {
		return PartyDto.of(partyApplyUseCase.apply(partyId, memberId, LocalDateTime.now()));
	}

	public ParticipationDto leave(long partyId, long memberId) {
		return partyUseCase.leave(partyId, memberId, LocalDateTime.now()).toDto();
	}

	@Transactional(readOnly = true)
	public PartyRaceStatusDto findMyRaceStatus(long raceId, long memberId) {
		MyParticipation myParticipation = participationRepository.findActiveByRaceIdAndMemberId(raceId, memberId)
			.map(MyParticipation::from)
			.orElse(null);
		return partyUseCase.findMyRaceParty(raceId, memberId, LocalDateTime.now())
			.map(mine -> PartyRaceStatusDto.of(mine, memberId, PartyUseCase.MIN_PARTY_SIZE, null, myParticipation))
			.orElseGet(() -> PartyRaceStatusDto.none(myParticipation));
	}
}
