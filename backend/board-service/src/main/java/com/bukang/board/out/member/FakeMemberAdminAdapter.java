package com.bukang.board.out.member;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 가짜 어댑터: 회원 서비스를 띄우지 않고 개발할 때 누구나 관리자로 본다 (board.clients.member.mode=fake일 때만)
 * mode가 없으면 켜지지 않아(matchIfMissing 없음), 설정이 빠지면 누구나 관리자가 되는 대신 앱이 뜨지 않는다
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "board.clients.member", name = "mode", havingValue = "fake")
public class FakeMemberAdminAdapter implements MemberAdminPort {
	public FakeMemberAdminAdapter() {
		log.warn("관리자 확인은 가짜 응답(FakeMemberAdminAdapter)을 씁니다. 누구나 관리자입니다. 실제 회원 서비스는 board.clients.member.mode=http");
	}

	@Override
	public boolean isAdmin(long memberId) {
		return true;
	}
}
