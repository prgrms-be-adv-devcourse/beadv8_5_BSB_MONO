package com.bukang.member.crew.app;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.member.crew.domain.CrewRole;
import com.bukang.member.crew.out.CrewMemberRepository;

import lombok.RequiredArgsConstructor;

/**
 * 회원 영역이 크루 영역을 부르는 공개 메서드 (회원 탈퇴 때만 쓴다)
 * 회원 영역은 크루 엔티티·저장소를 직접 만지지 않고 이 서비스로만 부른다
 */
@Service
@RequiredArgsConstructor
public class CrewMembershipService {
	private final CrewMemberRepository crewMemberRepository;

	@Transactional(readOnly = true)
	public boolean isLeaderOfAnyCrew(long memberId) {
		return crewMemberRepository.existsByMemberIdAndRole(memberId, CrewRole.LEADER);
	}

	// 호출한 쪽(회원 탈퇴)의 트랜잭션 안에서 같이 처리한다
	@Transactional
	public void leaveAllCrews(long memberId) {
		crewMemberRepository.deleteAllByMemberId(memberId);
	}
}
