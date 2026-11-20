package com.bukang.member.crew.app;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.common.shared.crew.event.CrewLeaderChangedEvent;
import com.bukang.common.shared.crew.event.CrewMemberLeftReason;
import com.bukang.member.crew.domain.Crew;
import com.bukang.member.crew.domain.CrewMember;
import com.bukang.member.crew.domain.CrewRemoval;
import com.bukang.member.crew.domain.CrewRole;
import com.bukang.member.crew.domain.exception.CrewAlreadyLeaderException;
import com.bukang.member.crew.domain.exception.CrewCannotRemoveLeaderException;
import com.bukang.member.crew.domain.exception.CrewHasOtherMembersException;
import com.bukang.member.crew.domain.exception.CrewLeaderCannotLeaveException;
import com.bukang.member.crew.domain.exception.CrewMemberNotFoundException;
import com.bukang.member.crew.domain.exception.CrewNotMemberException;
import com.bukang.member.crew.dto.CrewHomeDto;
import com.bukang.member.crew.out.CrewInviteRepository;
import com.bukang.member.crew.out.CrewMemberProfilePort;
import com.bukang.member.crew.out.CrewMemberRepository;
import com.bukang.member.crew.out.CrewRemovalRepository;
import com.bukang.member.crew.out.CrewRepository;

import lombok.RequiredArgsConstructor;

/**
 * 크루 만들기, 이름 바꾸기, 크루장 넘기기, 없애기, 나가기, 크루원 내보내기 (BE-027, BE-032)
 * 크루원 구성을 바꾸는 요청은 크루 행을 먼저 잠가(findActiveByIdForUpdate) 같은 크루의 요청을 차례로 처리한다.
 * MySQL 기본 격리 수준(REPEATABLE READ)은 첫 일반 읽기 때 읽는 시점을 정하므로, 이 메서드들은 잠금보다 앞에 일반 읽기를 두지 않는다.
 * 그래야 잠금 뒤의 일반 읽기가 앞 요청이 커밋한 값을 본다
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CrewManageCommand {
	private final CrewLoader loader;
	private final CrewQuery crewQuery;
	private final CrewRepository crewRepository;
	private final CrewMemberRepository crewMemberRepository;
	private final CrewInviteRepository crewInviteRepository;
	private final CrewRemovalRepository crewRemovalRepository;
	private final CrewMemberProfilePort profilePort;
	private final ApplicationEventPublisher eventPublisher;

	public CrewHomeDto createCrew(long memberId, String name) {
		LocalDateTime now = loader.now();
		Crew crew = crewRepository.save(Crew.create(name, now));
		// 만든 회원이 크루장이고, 가입 시각은 크루를 만든 시각과 같다
		crewMemberRepository.save(CrewMember.leader(crew.getId(), memberId, now));
		return crewQuery.homeOf(crew, memberId);
	}

	public void leave(long memberId, long crewId) {
		loader.lockActiveCrew(crewId);
		CrewMember membership = loader.findMembership(crewId, memberId);
		if (!membership.getRole().canLeave()) {
			throw new CrewLeaderCannotLeaveException();
		}
		// 회원 탈퇴는 크루 행을 잠그지 않아 같은 행을 먼저 지울 수 있다. 지운 행 수로 확인한다
		if (crewMemberRepository.deleteByCrewIdAndMemberId(crewId, memberId) == 0) {
			throw new CrewNotMemberException();
		}
		publishLeft(membership, CrewMemberLeftReason.LEFT);
	}

	public void removeMember(long memberId, long crewId, long targetMemberId) {
		loader.lockActiveCrew(crewId);
		loader.requireLeader(loader.findMembership(crewId, memberId));
		// 크루장은 요청한 사람 한 명뿐이다
		if (targetMemberId == memberId) {
			throw new CrewCannotRemoveLeaderException();
		}
		// 이벤트에 실을 가입 시각을 지우기 전에 읽는다. 다른 크루의 크루원이면 여기서 404다
		CrewMember target = loader.findTarget(crewId, targetMemberId);
		// 회원 탈퇴가 같은 행을 읽은 뒤 먼저 지웠으면 0이다
		if (crewMemberRepository.deleteByCrewIdAndMemberId(crewId, targetMemberId) == 0) {
			throw new CrewMemberNotFoundException();
		}
		// 내보내기 전에 만든 초대 링크로 다시 들어오지 못하게 남긴다 (BUK-135)
		crewRemovalRepository.save(CrewRemoval.of(crewId, targetMemberId, loader.now()));
		publishLeft(target, CrewMemberLeftReason.REMOVED);
	}

	public CrewHomeDto transferLeader(long memberId, long crewId, long targetMemberId) {
		// 넘겨받을 사람의 회원 탈퇴와 겹치면 크루장이 없는 크루가 생길 수 있어 그 회원 행을 먼저 잠근다.
		// 회원 탈퇴도 같은 행을 잠근 뒤 「크루장인 크루가 있는지」 보므로 둘 중 하나가 커밋된 뒤에 다른 하나가 진행된다.
		// 순서는 가입과 같다 (회원 행 → 크루 행)
		boolean targetActive = profilePort.lockActiveMember(targetMemberId);
		Crew crew = loader.lockActiveCrew(crewId);
		CrewMember leader = loader.findMembership(crewId, memberId);
		loader.requireLeader(leader);
		if (targetMemberId == memberId) {
			throw new CrewAlreadyLeaderException();
		}
		if (!targetActive) {
			throw new CrewMemberNotFoundException();
		}
		CrewMember target = loader.findTarget(crewId, targetMemberId);

		LocalDateTime now = loader.now();
		leader.changeRole(CrewRole.MEMBER, now);
		target.changeRole(CrewRole.LEADER, now);
		// 역할을 바꾼 같은 트랜잭션에서 아웃박스에 남긴다. 크루장이 바뀌는 곳은 여기뿐이다 (BUK-99)
		eventPublisher.publishEvent(
			new CrewLeaderChangedEvent(UUID.randomUUID().toString(), crewId, memberId, targetMemberId, now));
		return crewQuery.homeOf(crew, memberId);
	}

	public void disband(long memberId, long crewId) {
		Crew crew = loader.lockActiveCrew(crewId);
		CrewMember leader = loader.findMembership(crewId, memberId);
		loader.requireLeader(leader);
		if (crewMemberRepository.countByCrewId(crewId) > 1) {
			throw new CrewHasOtherMembersException();
		}
		// 상태를 먼저 바꾼다. 아래 일괄 삭제가 바뀐 내용을 반영(flush)한 뒤 영속성 컨텍스트를 비운다
		crew.disband(loader.now());
		crewInviteRepository.deleteAllByCrewId(crewId);
		crewRemovalRepository.deleteAllByCrewId(crewId);
		// 위에서 크루장 혼자인 것을 확인했으므로 빠진 사람은 크루장 한 명이다. 아니면 이벤트가 모자라므로 롤백한다
		if (crewMemberRepository.deleteAllByCrewId(crewId) != 1) {
			throw new IllegalStateException("크루를 없앨 때 크루장 말고 다른 크루원 행이 있습니다. crewId=" + crewId);
		}
		publishLeft(leader, CrewMemberLeftReason.DISBANDED);
	}

	public CrewHomeDto rename(long memberId, long crewId, String name) {
		Crew crew = loader.lockActiveCrew(crewId);
		loader.requireLeader(loader.findMembership(crewId, memberId));
		crew.rename(name, loader.now());
		return crewQuery.homeOf(crew, memberId);
	}

	// 크루원 행을 지운 같은 트랜잭션에서 아웃박스에 남긴다 (MemberOutboxAppendUseCase, BE-032)
	private void publishLeft(CrewMember membership, CrewMemberLeftReason reason) {
		eventPublisher.publishEvent(membership.leftEvent(reason, loader.now()));
	}
}
