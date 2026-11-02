package com.bukang.order.support;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.out.member.MemberAdminPort;
import com.bukang.order.out.member.MemberNicknamePort;

/**
 * 테스트용 회원 서비스. 테스트가 정한 회원만 관리자이고(가짜 어댑터처럼 누구나 관리자가 되지 않는다),
 * 테스트가 정한 닉네임만 찾는다. 회원 서비스가 멈춘 상황도 만들 수 있다
 */
public class StubMemberPorts implements MemberAdminPort, MemberNicknamePort {
	static final String UNAVAILABLE_MESSAGE = "회원 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해주세요.";

	private final Set<Long> adminIds = ConcurrentHashMap.newKeySet();
	private final Map<String, Long> memberIdByNickname = new ConcurrentHashMap<>();
	private volatile boolean unavailable;

	// 물어볼 때 DB 트랜잭션 안이었는지 (회원 서비스를 기다리는 동안 DB 연결을 잡지 않는지 확인할 때 쓴다)
	private final List<Boolean> askedInTransaction = new CopyOnWriteArrayList<>();

	@Override
	public boolean isAdmin(long memberId) {
		ask();
		return adminIds.contains(memberId);
	}

	@Override
	public Optional<Long> findMemberId(String nickname) {
		ask();
		return Optional.ofNullable(memberIdByNickname.get(nickname));
	}

	private void ask() {
		askedInTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
		if (unavailable) {
			throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
		}
	}

	public void admin(long memberId) {
		adminIds.add(memberId);
	}

	public void member(String nickname, long memberId) {
		memberIdByNickname.put(nickname, memberId);
	}

	public void setUnavailable(boolean unavailable) {
		this.unavailable = unavailable;
	}

	public List<Boolean> askedInTransaction() {
		return askedInTransaction;
	}

	public void clear() {
		adminIds.clear();
		memberIdByNickname.clear();
		unavailable = false;
		askedInTransaction.clear();
	}
}
