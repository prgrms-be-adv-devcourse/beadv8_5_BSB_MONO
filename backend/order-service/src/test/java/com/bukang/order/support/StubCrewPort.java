package com.bukang.order.support;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.bukang.order.out.crew.CrewPort;

/**
 * 테스트용 크루. 테스트가 정한 회원만 크루원이 아니고, 나머지는 모두 크루원이다. 크루는 모두 있다
 */
public class StubCrewPort implements CrewPort {
	static final LocalDateTime JOINED_AT = LocalDateTime.of(2026, 1, 1, 0, 0);

	private final Set<Long> notCrewMembers = ConcurrentHashMap.newKeySet();

	@Override
	public Optional<CrewMembership> findMembership(long crewId, long memberId) {
		boolean member = !notCrewMembers.contains(memberId);
		return Optional.of(new CrewMembership(member, member ? JOINED_AT : null));
	}

	@Override
	public OptionalInt countMembers(long crewId) {
		return OptionalInt.of(10);
	}

	public void notCrewMember(long memberId) {
		notCrewMembers.add(memberId);
	}

	public void clear() {
		notCrewMembers.clear();
	}
}
