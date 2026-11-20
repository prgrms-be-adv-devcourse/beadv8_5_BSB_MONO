package com.bukang.order.out.member;

import java.time.LocalDate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 가짜 어댑터: 회원 서비스를 띄우지 않고 개발할 때 누구나 같은 본인인증 값을 가진 것으로 본다 (CRU-59)
 * order.clients.member.mode=fake일 때만 쓴다. 운영(prod)은 http라 HttpMemberIdentityAdapter가 회원 서비스에 묻는다
 */
@Component
@ConditionalOnProperty(prefix = "order.clients.member", name = "mode", havingValue = "fake")
public class FakeMemberIdentityAdapter implements MemberIdentityPort {
	public static final MemberIdentity IDENTITY = new MemberIdentity("김러너", LocalDate.of(1994, 6, 25), "010-1234-5678");

	@Override
	public MemberIdentity find(long memberId) {
		return IDENTITY;
	}
}
