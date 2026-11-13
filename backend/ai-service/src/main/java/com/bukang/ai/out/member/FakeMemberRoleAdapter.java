package com.bukang.ai.out.member;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 가짜 어댑터: 회원 서비스를 띄우지 않고 개발할 때 누구나 관리자이자 승인된 판매자로 본다
 * ai.clients.member.mode=fake라고 적었을 때만 쓴다. mode가 없으면 켜지지 않고 앱이 뜨지 않는다 (누구나 관리자가 되지 않게)
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ai.clients.member", name = "mode", havingValue = "fake")
public class FakeMemberRoleAdapter implements MemberRolePort {
	public FakeMemberRoleAdapter() {
		log.warn("역할 확인은 가짜 응답(FakeMemberRoleAdapter)을 씁니다. 누구나 관리자·판매자입니다. 실제 회원 서비스는 ai.clients.member.mode=http");
	}

	@Override
	public boolean isAdmin(long memberId) {
		return true;
	}

	@Override
	public boolean isApprovedSeller(long memberId) {
		return true;
	}
}
