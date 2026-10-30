package com.bukang.member.out;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.bukang.member.crew.out.CrewMemberProfilePort;
import com.bukang.member.domain.Member;

import lombok.RequiredArgsConstructor;

// 크루 영역이 닉네임을 읽는 포트의 회원 서비스 안 구현
@Component
@RequiredArgsConstructor
public class MemberCrewProfileAdapter implements CrewMemberProfilePort {
	private final MemberRepository memberRepository;

	@Override
	public Map<Long, String> findNicknames(Collection<Long> memberIds) {
		Map<Long, String> nicknames = new HashMap<>();
		memberRepository.findAllById(memberIds).forEach(member ->
			// 탈퇴한 회원은 닉네임이 비어 있으므로 다른 서비스 사본과 같은 이름으로 보여 준다
			nicknames.put(member.getId(), member.getNickname() != null ? member.getNickname()
				: Member.WITHDRAWN_NICKNAME));
		return nicknames;
	}
}
