package com.bukang.member.crew.app;

import java.time.LocalDateTime;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.common.shared.member.web.LoginRequiredException;
import com.bukang.member.crew.domain.Crew;
import com.bukang.member.crew.domain.CrewInvite;
import com.bukang.member.crew.domain.CrewInvite.UnusableReason;
import com.bukang.member.crew.domain.CrewMember;
import com.bukang.member.crew.domain.exception.CrewAlreadyMemberException;
import com.bukang.member.crew.domain.exception.CrewInviteAlreadyUsedException;
import com.bukang.member.crew.domain.exception.CrewInviteCancelForbiddenException;
import com.bukang.member.crew.domain.exception.CrewInviteCanceledException;
import com.bukang.member.crew.domain.exception.CrewInviteExpiredException;
import com.bukang.member.crew.domain.exception.CrewInviteNotFoundException;
import com.bukang.member.crew.dto.CrewInviteCreatedDto;
import com.bukang.member.crew.dto.CrewJoinedDto;
import com.bukang.member.crew.out.CrewInviteRepository;
import com.bukang.member.crew.out.CrewMemberProfilePort;
import com.bukang.member.crew.out.CrewMemberRepository;
import com.bukang.member.crew.out.CrewRepository;

import lombok.RequiredArgsConstructor;

/**
 * 초대 링크 만들기·취소, 초대 링크로 가입 (BE-028~029)
 * 가입은 회원 행 → 크루 행 → 초대 행 순서로 잠근다. 크루 없애기·크루장 넘기기와 같은 순서다
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CrewInviteCommand {
	private final CrewLoader loader;
	private final CrewRepository crewRepository;
	private final CrewMemberRepository crewMemberRepository;
	private final CrewInviteRepository crewInviteRepository;
	private final CrewMemberProfilePort profilePort;
	private final ApplicationEventPublisher eventPublisher;

	public CrewInviteCreatedDto createInvite(long memberId, long crewId) {
		// 크루 없애기와 겹치면 없앤 크루에 초대 행이 남을 수 있어 크루 행을 잠근다
		loader.lockActiveCrew(crewId);
		loader.requireCanInvite(loader.findMembership(crewId, memberId));

		LocalDateTime now = loader.now();
		String token = CrewInviteToken.generate();
		CrewInvite invite = crewInviteRepository.save(
			CrewInvite.create(crewId, token, CrewInviteToken.hash(token), memberId, now));
		// 토큰 원문은 로그에 남기지 않는다
		return CrewInviteCreatedDto.of(invite);
	}

	public void cancelInvite(long memberId, long crewId, long inviteId) {
		loader.findActiveCrew(crewId);
		CrewMember membership = loader.findMembership(crewId, memberId);
		CrewInvite invite = crewInviteRepository.findByIdAndCrewId(inviteId, crewId)
			.orElseThrow(CrewInviteNotFoundException::new);
		// 보낸 사람이나 크루장만 취소한다
		if (invite.getCreatedBy() != memberId && !membership.getRole().canManage()) {
			throw new CrewInviteCancelForbiddenException();
		}
		// 가입과 동시에 일어나도 둘 중 하나만 성공하도록 조건을 건 UPDATE로 취소한다
		LocalDateTime now = loader.now();
		if (crewInviteRepository.markCanceled(inviteId, memberId, now) == 0) {
			CrewInvite current = crewInviteRepository.findByIdForUpdate(inviteId)
				.orElseThrow(CrewInviteNotFoundException::new);
			// 쓰임·취소·만료 어느 것도 아니면 막 만료된 것으로 본다
			throw cancelingError(current.unusableReason(now).orElse(UnusableReason.EXPIRED));
		}
	}

	public CrewJoinedDto join(long memberId, String inviteToken) {
		// 회원 탈퇴와 겹치면 탈퇴한 회원이 크루에 남을 수 있어 회원 행을 먼저 잠근다.
		// 탈퇴가 먼저 끝났으면 로그인이 풀린 회원으로 본다 (탈퇴하면 재발급이 막혀 곧 로그아웃된다)
		if (!profilePort.lockActiveMember(memberId)) {
			throw new LoginRequiredException();
		}
		CrewInvite invite = loader.findInviteByToken(inviteToken);
		// 크루 없애기와 겹치면 없앤 크루에 크루원이 남을 수 있어 크루 행을 잠근다.
		// 없애기가 먼저 끝났으면 링크가 사라진 것으로 보고, 가입이 먼저면 없애기가 「다른 크루원 있음」으로 막힌다
		Crew crew = crewRepository.findActiveByIdForUpdate(invite.getCrewId())
			.orElseThrow(CrewInviteNotFoundException::new);
		// 이미 크루원이면 409로 답한다
		if (crewMemberRepository.existsByCrewIdAndMemberId(crew.getId(), memberId)) {
			throw new CrewAlreadyMemberException();
		}
		// 내보내기와 동시에 와도 다시 가입되지 않는다. 내보내기가 크루 락을 먼저 잡고 커밋하면,
		// 가입은 크루 락 전에 만든 MySQL 스냅숏으로 위 크루원 확인을 하므로 아직 크루원으로 보고 409로 막힌다
		loader.throwIfRemovedAfterInvite(invite, memberId);

		LocalDateTime now = loader.now();
		// 링크 하나로 24시간 동안 여러 명이 가입한다 (BUK-135). 취소와 동시에 와도 취소가 커밋된 뒤의 가입은 막는다.
		// 잠금 순서는 회원 → 크루 → 초대로 크루 없애기와 같다
		if (crewInviteRepository.touchIfJoinable(invite.getId(), now) == 0) {
			CrewInvite current = crewInviteRepository.findByIdForUpdate(invite.getId())
				.orElseThrow(CrewInviteNotFoundException::new);
			loader.throwIfUnusableWhenOpeningLink(current, now);
			// 쓰임·취소·만료는 되돌릴 수 없으므로 여기에 오지 않는다
			throw new IllegalStateException("쓸 수 있는 초대인데 가입에 쓰지 못했습니다. inviteId=" + invite.getId());
		}
		CrewMember member;
		try {
			member = crewMemberRepository.saveAndFlush(
				CrewMember.invited(crew.getId(), memberId, invite.getId(), now));
		} catch (DataIntegrityViolationException exception) {
			// 같은 회원이 같은 크루에 동시에 가입하면 uk_crew_member가 두 번째 행을 막는다
			// (보통은 위의 회원 행 잠금 뒤 크루원 확인이 먼저 「이미 크루원」으로 답한다)
			throw new CrewAlreadyMemberException();
		}
		// 크루원 행을 만든 같은 트랜잭션에서 아웃박스에 남긴다 (MemberOutboxAppendUseCase, BUK-99).
		// 위 try 밖에 두어, 아웃박스 저장 실패가 「이미 크루원」으로 바뀌지 않게 한다
		eventPublisher.publishEvent(member.joinedEvent());
		// 트랜잭션 안에서 낸다. 커밋 뒤에 common이 보내므로 롤백되면 알림도 나가지 않는다
		eventPublisher.publishEvent(CrewNotifications.joined(memberId, crew));
		return new CrewJoinedDto(crew.getId(), crew.getName(), member.getRole(), member.getJoinedAt());
	}

	// 취소할 때는 409다 (링크를 열거나 가입할 때는 CrewLoader가 410으로 답한다)
	private RuntimeException cancelingError(UnusableReason reason) {
		return switch (reason) {
			case USED -> CrewInviteAlreadyUsedException.whenCanceling();
			case CANCELED -> CrewInviteCanceledException.whenCanceling();
			case EXPIRED -> CrewInviteExpiredException.whenCanceling();
		};
	}
}
