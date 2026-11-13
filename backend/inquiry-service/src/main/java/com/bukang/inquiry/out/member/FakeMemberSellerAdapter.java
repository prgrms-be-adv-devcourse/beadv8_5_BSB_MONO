package com.bukang.inquiry.out.member;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 가짜 어댑터: 회원 서비스를 띄우지 않고 개발할 때 누구나 승인된 판매자로 본다
 * 관리자 확인과 같은 설정(inquiry.clients.member.mode=fake)으로 켜진다. 운영은 http라 HttpMemberSellerAdapter가 답한다
 */
@Component
@ConditionalOnProperty(prefix = "inquiry.clients.member", name = "mode", havingValue = "fake")
public class FakeMemberSellerAdapter implements MemberSellerPort {
	@Override
	public boolean isApprovedSeller(long memberId) {
		return true;
	}
}
