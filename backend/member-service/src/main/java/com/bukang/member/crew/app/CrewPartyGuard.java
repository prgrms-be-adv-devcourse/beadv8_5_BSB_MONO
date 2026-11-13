package com.bukang.member.crew.app;

import org.springframework.stereotype.Service;

import com.bukang.member.crew.domain.CrewMember;
import com.bukang.member.crew.domain.CrewRole;
import com.bukang.member.crew.domain.exception.CrewKickPartyBlockedException;
import com.bukang.member.crew.domain.exception.CrewLeavePartyBlockedException;
import com.bukang.member.crew.out.CrewMemberRepository;
import com.bukang.member.out.order.CrewPartyStatusPort;

import lombok.RequiredArgsConstructor;

/**
 * 크루 나가기·내보내기 전에 주문 서비스에 파티 상태를 묻고, 막힌 파티가 있으면 거절한다 (BUK-102)
 * 트랜잭션을 만들지 않는다. 컨트롤러가 CrewFacade.leave·removeMember보다 먼저 불러, 거절되면 크루원 행·아웃박스가 바뀌지 않고
 * 외부 호출 동안 크루 행 락도 잡지 않는다
 * 요청한 사람이 나갈 수 있는 크루원(나가기)·크루장(내보내기)일 때만 묻는다. 아니면 CrewFacade가 403·404·409로 답하므로
 * 권한 없는 사람에게 다른 크루원의 파티 상태가 드러나지 않는다
 * 대상이 이 크루의 크루원일 때만 묻는다. 크루원이 아니면 CrewFacade가 404로 답한다
 * shortcut: 확인과 삭제 사이에 파티 상태나 요청자의 역할이 바뀌는 경우는 막지 못한다 (예: 크루장 넘기기와 나가기를 동시에 보냄),
 * 필요하면 CrewFacade 락 안에서 역할을 다시 확인한다
 */
@Service
@RequiredArgsConstructor
public class CrewPartyGuard {
	private final CrewMemberRepository crewMemberRepository;
	private final CrewPartyStatusPort partyStatusPort;

	public void checkLeave(long memberId, long crewId) {
		boolean leavable = crewMemberRepository.findByCrewIdAndMemberId(crewId, memberId)
			.map(member -> member.getRole() != CrewRole.LEADER)
			.orElse(false);
		if (leavable && !partyStatusPort.findStatus(crewId, memberId).canLeave()) {
			throw new CrewLeavePartyBlockedException();
		}
	}

	public void checkKick(long memberId, long crewId, long targetMemberId) {
		boolean leader = crewMemberRepository.findByCrewIdAndMemberId(crewId, memberId)
			.map(CrewMember::getRole)
			.filter(CrewRole.LEADER::equals)
			.isPresent();
		boolean targetIsMember = leader && crewMemberRepository.existsByCrewIdAndMemberId(crewId, targetMemberId);
		if (targetIsMember && targetMemberId != memberId
			&& !partyStatusPort.findStatus(crewId, targetMemberId).canKick()) {
			throw new CrewKickPartyBlockedException();
		}
	}
}
