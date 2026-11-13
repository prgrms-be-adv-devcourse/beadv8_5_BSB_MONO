package com.bukang.inquiry.support;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.bukang.inquiry.domain.exception.InquiryExternalServiceUnavailableException;
import com.bukang.inquiry.out.member.MemberNicknamesPort;

/**
 * 테스트용 회원 서비스(닉네임). 테스트가 정한 회원만 찾고(없는 회원은 결과에서 빠진다), 멈춘 상황도 만들 수 있다
 */
public class StubMemberNicknamesPort implements MemberNicknamesPort {
	private final Map<Long, String> nicknames = new ConcurrentHashMap<>();
	private volatile boolean unavailable;

	@Override
	public Map<Long, String> findNicknames(Collection<Long> memberIds) {
		if (unavailable) {
			throw new InquiryExternalServiceUnavailableException("회원 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.");
		}
		Map<Long, String> found = new HashMap<>();
		memberIds.forEach(id -> {
			if (nicknames.containsKey(id)) {
				found.put(id, nicknames.get(id));
			}
		});
		return found;
	}

	public void nickname(long memberId, String nickname) {
		nicknames.put(memberId, nickname);
	}

	public void setUnavailable(boolean unavailable) {
		this.unavailable = unavailable;
	}

	public void clear() {
		nicknames.clear();
		unavailable = false;
	}
}
