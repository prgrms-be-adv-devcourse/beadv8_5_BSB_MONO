package com.bukang.inquiry.support;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.bukang.inquiry.domain.exception.InquiryExternalServiceUnavailableException;
import com.bukang.inquiry.out.member.MemberAdminPort;

/**
 * 테스트용 회원 서비스. 테스트가 정한 회원만 관리자이고(가짜 어댑터처럼 누구나 관리자가 되지 않는다),
 * 회원 서비스가 멈춘 상황도 만들 수 있다
 */
public class StubMemberAdminPort implements MemberAdminPort {
	private final Set<Long> adminIds = ConcurrentHashMap.newKeySet();
	private volatile boolean unavailable;

	@Override
	public boolean isAdmin(long memberId) {
		if (unavailable) {
			throw new InquiryExternalServiceUnavailableException("회원 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.");
		}
		return adminIds.contains(memberId);
	}

	public void admin(long memberId) {
		adminIds.add(memberId);
	}

	public void setUnavailable(boolean unavailable) {
		this.unavailable = unavailable;
	}

	public void clear() {
		adminIds.clear();
		unavailable = false;
	}
}
