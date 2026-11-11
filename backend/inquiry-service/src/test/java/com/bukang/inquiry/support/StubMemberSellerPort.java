package com.bukang.inquiry.support;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.bukang.inquiry.domain.exception.InquiryExternalServiceUnavailableException;
import com.bukang.inquiry.out.member.MemberSellerPort;

/**
 * 테스트용 회원 서비스(판매자 확인). 테스트가 정한 회원만 승인된 판매자이고, 회원 서비스가 멈춘 상황도 만들 수 있다
 */
public class StubMemberSellerPort implements MemberSellerPort {
	private final Set<Long> sellerIds = ConcurrentHashMap.newKeySet();
	private volatile boolean unavailable;

	@Override
	public boolean isApprovedSeller(long memberId) {
		if (unavailable) {
			throw new InquiryExternalServiceUnavailableException("회원 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.");
		}
		return sellerIds.contains(memberId);
	}

	public void seller(long memberId) {
		sellerIds.add(memberId);
	}

	public void setUnavailable(boolean unavailable) {
		this.unavailable = unavailable;
	}

	public void clear() {
		sellerIds.clear();
		unavailable = false;
	}
}
