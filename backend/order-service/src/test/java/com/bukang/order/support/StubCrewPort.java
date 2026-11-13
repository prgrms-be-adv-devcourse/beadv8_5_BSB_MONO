package com.bukang.order.support;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.bukang.order.out.crew.CrewPort;

/**
 * 테스트용 크루. 테스트가 정한 회원만 크루원이 아니고, 나머지는 모두 크루원이다. 테스트가 없앤 크루 말고는 모두 있다
 */
public class StubCrewPort implements CrewPort {
	public static final LocalDateTime JOINED_AT = LocalDateTime.of(2026, 1, 1, 0, 0);

	private final Set<Long> notCrewMembers = ConcurrentHashMap.newKeySet();
	private final Map<Long, LocalDateTime> joinedAts = new ConcurrentHashMap<>();
	private final Set<Long> missingCrews = ConcurrentHashMap.newKeySet();
	private final Map<Long, LocalDateTime> restrictionEnds = new ConcurrentHashMap<>();
	// 다음 크루 조회 직전에 한 번 돌릴 일 (조회와 잠금 사이에 다른 요청이 끼어드는 상황을 만든다)
	private final AtomicReference<Runnable> beforeNextLookup = new AtomicReference<>();

	@Override
	public Optional<CrewMembership> findMembership(long crewId, long memberId) {
		Runnable hook = beforeNextLookup.getAndSet(null);
		if (hook != null) {
			hook.run();
		}
		if (missingCrews.contains(crewId)) {
			return Optional.empty();
		}
		boolean member = !notCrewMembers.contains(memberId);
		return Optional.of(new CrewMembership(member, member ? joinedAts.getOrDefault(memberId, JOINED_AT) : null));
	}

	@Override
	public OptionalInt countMembers(long crewId) {
		return OptionalInt.of(10);
	}

	@Override
	public Optional<LocalDateTime> findPartyRestrictionEnd(long crewId) {
		return Optional.ofNullable(restrictionEnds.get(crewId));
	}

	// 결제율 미달로 파티 만들기가 제한 중인 크루 (BUK-190)
	public void restrictParty(long crewId, LocalDateTime endAt) {
		restrictionEnds.put(crewId, endAt);
	}

	public void notCrewMember(long memberId) {
		notCrewMembers.add(memberId);
	}

	// 크루를 나갔다 다시 가입한 것처럼 크루 가입 시각을 바꾼다
	public void rejoinedCrewAt(long memberId, LocalDateTime joinedAt) {
		joinedAts.put(memberId, joinedAt);
	}

	// 없거나 없앤 크루 (회원 서비스 404 CREW_NOT_FOUND)
	public void missingCrew(long crewId) {
		missingCrews.add(crewId);
	}

	public void beforeNextLookup(Runnable hook) {
		beforeNextLookup.set(hook);
	}

	public void clear() {
		beforeNextLookup.set(null);
		missingCrews.clear();
		restrictionEnds.clear();
		notCrewMembers.clear();
		joinedAts.clear();
	}
}
