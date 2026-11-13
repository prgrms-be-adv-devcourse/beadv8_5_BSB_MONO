package com.bukang.ai.support;

import java.util.Set;

import com.bukang.ai.domain.exception.AiExternalServiceUnavailableException;
import com.bukang.ai.out.member.MemberRolePort;

// 회원 서비스 대역. 정해 둔 회원만 판매자·관리자다
public class StubMemberRolePort implements MemberRolePort {
	private final Set<Long> sellers;
	private final Set<Long> admins;
	private volatile boolean unavailable;

	public StubMemberRolePort(Set<Long> sellers, Set<Long> admins) {
		this.sellers = sellers;
		this.admins = admins;
	}

	@Override
	public boolean isAdmin(long memberId) {
		failIfUnavailable();
		return admins.contains(memberId);
	}

	@Override
	public boolean isApprovedSeller(long memberId) {
		failIfUnavailable();
		return sellers.contains(memberId);
	}

	public void setUnavailable(boolean unavailable) {
		this.unavailable = unavailable;
	}

	private void failIfUnavailable() {
		if (unavailable) {
			throw new AiExternalServiceUnavailableException();
		}
	}
}
