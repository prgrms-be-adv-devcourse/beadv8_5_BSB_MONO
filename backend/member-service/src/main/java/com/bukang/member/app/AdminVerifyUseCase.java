package com.bukang.member.app;

import org.springframework.stereotype.Service;

import com.bukang.member.domain.Member;
import com.bukang.member.domain.exception.MemberNotAdminException;
import com.bukang.member.domain.exception.MemberNotFoundException;
import com.bukang.member.out.MemberRepository;

import lombok.RequiredArgsConstructor;

// 관리자 API를 부른 회원이 관리자인지 DB의 역할 칸으로 확인한다 (BE-020 결정 1)
// 토큰에는 역할이 없어서, 게이트웨이가 넘겨준 X-Member-Id로 회원을 읽어 본다
@Service
@RequiredArgsConstructor
public class AdminVerifyUseCase {
	private final MemberRepository memberRepository;

	public Member verify(long memberId) {
		Member member = findActive(memberId);
		if (!member.isAdmin()) {
			throw new MemberNotAdminException("관리자만 이용할 수 있습니다.");
		}
		return member;
	}

	// 다른 서비스(정산)가 내부 API로 묻는다 (PRO-265). 관리자가 아니어도 예외 없이 false로 답하고, 403은 묻는 쪽이 정한다
	// 없거나 탈퇴한 회원은 verify와 같이 404로 답해, 두 서비스가 같은 기준으로 관리자를 판정한다
	public boolean isAdmin(long memberId) {
		return findActive(memberId).isAdmin();
	}

	// 탈퇴한 관리자는 남은 Access Token으로 불러도 없는 회원으로 본다 (PRO-217, BE-022 결정 6)
	private Member findActive(long memberId) {
		return memberRepository.findActiveById(memberId)
			.orElseThrow(() -> new MemberNotFoundException("존재하지 않는 회원입니다."));
	}
}
