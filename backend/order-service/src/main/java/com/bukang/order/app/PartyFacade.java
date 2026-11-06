package com.bukang.order.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.order.dto.ParticipationDto;
import com.bukang.order.dto.PartyCreateRequestDto;
import com.bukang.order.dto.PartyCreatedDto;
import com.bukang.order.dto.PartyDto;
import com.bukang.order.dto.PartyJoinRequestDto;
import com.bukang.order.dto.PartyRaceStatusDto;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class PartyFacade {
	private final PartyUseCase partyUseCase;

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

	public ParticipationDto leave(long partyId, long memberId) {
		return partyUseCase.leave(partyId, memberId, LocalDateTime.now()).toDto();
	}

	@Transactional(readOnly = true)
	public PartyRaceStatusDto findMyRaceStatus(long raceId, long memberId) {
		return partyUseCase.findMyRaceParty(raceId, memberId)
			.map(mine -> PartyRaceStatusDto.of(mine, memberId, PartyUseCase.MIN_PARTY_SIZE,
				PartyUseCase.MAX_PARTY_SIZE))
			.orElseGet(PartyRaceStatusDto::none);
	}
}
