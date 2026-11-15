package com.bukang.cash.out.member;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 가짜 어댑터: 회원 서비스를 띄우지 않고 개발할 때 누구나 관리자로 본다
 * cash.clients.member.mode=fake라고 적었을 때만 쓴다. 운영(prod)은 http라 HttpMemberAdminAdapter가 회원 서비스에 묻는다
 * mode가 없을 때 켜지지 않는다(matchIfMissing 없음). 설정이 빠지면 누구나 관리자가 되는 대신 관리자 확인 빈이 없어 앱이 뜨지 않는다
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "cash.clients.member", name = "mode", havingValue = "fake")
public class FakeMemberAdminAdapter implements MemberAdminPort {
	// 호출마다 경고를 남기면 로그가 묻히므로, 앱을 켤 때 한 번만 알린다
	public FakeMemberAdminAdapter() {
		log.warn("관리자 확인은 가짜 응답(FakeMemberAdminAdapter)을 씁니다. 누구나 관리자입니다. 실제 회원 서비스는 cash.clients.member.mode=http");
	}

	@Override
	public boolean isAdmin(long memberId) {
		return true;
	}
}
