package com.bukang.member.crew.app;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.bukang.member.crew.domain.Crew;
import com.bukang.member.crew.domain.CrewInvite;
import com.bukang.member.crew.domain.CrewInvite.UnusableReason;
import com.bukang.member.crew.domain.CrewMember;
import com.bukang.member.crew.domain.exception.CrewInviteAlreadyUsedException;
import com.bukang.member.crew.domain.exception.CrewInviteCanceledException;
import com.bukang.member.crew.domain.exception.CrewInviteExpiredException;
import com.bukang.member.crew.domain.exception.CrewInviteForbiddenException;
import com.bukang.member.crew.domain.exception.CrewInviteNotFoundException;
import com.bukang.member.crew.domain.exception.CrewInviteRemovedMemberException;
import com.bukang.member.crew.domain.exception.CrewLeaderOnlyException;
import com.bukang.member.crew.domain.exception.CrewMemberNotFoundException;
import com.bukang.member.crew.domain.exception.CrewNotFoundException;
import com.bukang.member.crew.domain.exception.CrewNotMemberException;
import com.bukang.member.crew.out.CrewInviteRepository;
import com.bukang.member.crew.out.CrewMemberRepository;
import com.bukang.member.crew.out.CrewRemovalRepository;
import com.bukang.member.crew.out.CrewRepository;

import lombok.RequiredArgsConstructor;

/**
 * CrewQuery·CrewManageCommand·CrewInviteCommand가 함께 쓰는 조회·잠금·검사·현재 시각
 * 트랜잭션을 만들지 않는다. 부른 쪽의 트랜잭션 안에서 실행된다
 * 크루원 구성을 바꾸는 요청은 크루 행을 먼저 잠그므로(lockActiveCrew), 잠금보다 앞에 일반 읽기를 두지 않는다
 * 현재 시각은 Clock으로 읽어 테스트에서 시간을 바꿀 수 있게 한다
 */
@Component
@RequiredArgsConstructor
class CrewLoader {
	private final CrewRepository crewRepository;
	private final CrewMemberRepository crewMemberRepository;
	private final CrewInviteRepository crewInviteRepository;
	private final CrewRemovalRepository crewRemovalRepository;
	private final Clock clock;

	Crew findActiveCrew(long crewId) {
		return crewRepository.findActiveById(crewId).orElseThrow(CrewNotFoundException::new);
	}

	Crew lockActiveCrew(long crewId) {
		return crewRepository.findActiveByIdForUpdate(crewId).orElseThrow(CrewNotFoundException::new);
	}

	CrewMember findMembership(long crewId, long memberId) {
		return crewMemberRepository.findByCrewIdAndMemberId(crewId, memberId)
			.orElseThrow(CrewNotMemberException::new);
	}

	// 내보내거나 크루장을 넘길 대상. 크루 ID로 범위를 묶어 다른 크루의 크루원은 찾지 않는다
	CrewMember findTarget(long crewId, long targetMemberId) {
		return crewMemberRepository.findByCrewIdAndMemberId(crewId, targetMemberId)
			.orElseThrow(CrewMemberNotFoundException::new);
	}

	CrewInvite findInviteByToken(String inviteToken) {
		return crewInviteRepository.findByTokenHash(CrewInviteToken.hash(inviteToken))
			.orElseThrow(CrewInviteNotFoundException::new);
	}

	Crew findCrewOfInvite(CrewInvite invite) {
		return crewRepository.findActiveById(invite.getCrewId())
			.orElseThrow(CrewInviteNotFoundException::new);
	}

	void requireLeader(CrewMember membership) {
		if (!membership.getRole().canManage()) {
			throw new CrewLeaderOnlyException();
		}
	}

	void requireCanInvite(CrewMember membership) {
		if (!membership.getRole().canInvite()) {
			throw new CrewInviteForbiddenException();
		}
	}

	// 링크를 열거나 가입할 때는 410이다 (취소할 때는 CrewInviteCommand가 409로 답한다)
	void throwIfUnusableWhenOpeningLink(CrewInvite invite, LocalDateTime now) {
		invite.unusableReason(now).ifPresent(reason -> {
			throw openingLinkError(reason);
		});
	}

	void throwIfRemovedAfterInvite(CrewInvite invite, long memberId) {
		if (crewRemovalRepository.existsByCrewIdAndMemberIdAndRemovedAtGreaterThanEqual(
			invite.getCrewId(), memberId, invite.getCreatedAt())) {
			throw new CrewInviteRemovedMemberException();
		}
	}

	LocalDateTime now() {
		return CrewTime.toServerLocal(clock.instant());
	}

	private RuntimeException openingLinkError(UnusableReason reason) {
		return switch (reason) {
			case USED -> CrewInviteAlreadyUsedException.whenOpeningLink();
			case CANCELED -> CrewInviteCanceledException.whenOpeningLink();
			case EXPIRED -> CrewInviteExpiredException.whenOpeningLink();
		};
	}
}
