package com.bukang.order.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.bukang.order.out.kit.KitOptionPort;

/**
 * 테스트용 키트 옵션 목록. 테스트마다 대회의 옵션과 준비 수량을 넣는다 (넣지 않은 대회는 옵션 없음)
 * markNotFound로 상품이 대회를 찾지 못한 경우(404)를 흉내 낸다
 */
public class StubKitOptionPort implements KitOptionPort {
	private final Map<Long, List<KitOption>> optionsByRace = new ConcurrentHashMap<>();
	private final Set<Long> notFoundRaces = ConcurrentHashMap.newKeySet();

	@Override
	public Optional<List<KitOption>> findKitOptions(long raceId) {
		if (notFoundRaces.contains(raceId)) {
			return Optional.empty();
		}
		return Optional.of(optionsByRace.getOrDefault(raceId, List.of()));
	}

	public void markNotFound(long raceId) {
		notFoundRaces.add(raceId);
	}

	public void add(long raceId, long kitOptionId, String name, String value, Integer stock) {
		optionsByRace.computeIfAbsent(raceId, id -> new ArrayList<>())
			.add(new KitOption(kitOptionId, name, value, stock));
	}

	public void clear() {
		optionsByRace.clear();
		notFoundRaces.clear();
	}
}
