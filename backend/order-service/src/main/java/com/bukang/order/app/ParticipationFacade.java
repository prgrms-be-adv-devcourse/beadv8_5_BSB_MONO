package com.bukang.order.app;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.order.domain.ParticipantInfo;
import com.bukang.order.dto.ParticipantInfoDto;
import com.bukang.order.dto.ParticipantInfoRequestDto;
import com.bukang.order.dto.ParticipationApplyRequestDto;
import com.bukang.order.dto.ParticipationDto;
import com.bukang.order.out.ParticipationRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ParticipationFacade {
	private final ParticipationApplyUseCase participationApplyUseCase;
	private final ParticipationRepository participationRepository;

	@Transactional
	public ParticipationDto apply(ParticipationApplyRequestDto request, long memberId) {
		return participationApplyUseCase.apply(
			memberId,
			request.getRaceCourseId(),
			request.getParticipant().toParticipantInfo(),
			LocalDateTime.now()
		).toDto();
	}

	@Transactional
	public ParticipationDto changeParticipant(long participationId, ParticipantInfoRequestDto request, long memberId) {
		return participationApplyUseCase.changeParticipant(
			participationId,
			memberId,
			request.toParticipantInfo(),
			LocalDateTime.now()
		).toDto();
	}

	@Transactional
	public ParticipationDto cancelBeforePayment(long participationId, long memberId) {
		return participationApplyUseCase.cancelBeforePayment(participationId, memberId, LocalDateTime.now()).toDto();
	}

	public ParticipationDto findById(long participationId, long memberId) {
		return participationApplyUseCase.findOwned(participationId, memberId).toDto();
	}

	// 다음 신청서에 채울 참가자 정보. 신청한 적이 없으면 빈 값이다 (정책 3장 1-2)
	// 마지막 신청이 키트·티셔츠 없는 종목이면 배송지·사이즈가 비어 있으므로, 각각 적은 최근 신청에서 채운다
	public Optional<ParticipantInfoDto> findLatestParticipant(long memberId) {
		return participationRepository.findFirstByMemberIdOrderByIdDesc(memberId)
			.map(participation -> ParticipantInfoDto.from(withLatestTshirtSize(memberId,
				withLatestShippingAddress(memberId, participation.getParticipant()))));
	}

	private ParticipantInfo withLatestTshirtSize(long memberId, ParticipantInfo participant) {
		if (participant.getTshirtSize() != null) {
			return participant;
		}
		return participationRepository.findFirstByMemberIdAndParticipantTshirtSizeIsNotNullOrderByIdDesc(memberId)
			.map(withSize -> participant.withTshirtSize(withSize.getParticipant().getTshirtSize()))
			.orElse(participant);
	}

	private ParticipantInfo withLatestShippingAddress(long memberId, ParticipantInfo participant) {
		if (participant.getShippingAddress() != null) {
			return participant;
		}
		return participationRepository
			.findFirstByMemberIdAndParticipantShippingAddressAddressIsNotNullOrderByIdDesc(memberId)
			.map(withAddress -> participant.withShippingAddress(withAddress.getParticipant().getShippingAddress()))
			.orElse(participant);
	}
}
