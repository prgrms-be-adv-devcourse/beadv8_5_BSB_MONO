package com.bukang.order.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.bukang.order.out.kit.KitOptionPort;

/**
 * 테스트용 키트 옵션 목록. 테스트마다 대회의 옵션과 준비 수량을 넣는다 (넣지 않은 대회는 옵션 없음)
 */
public class StubKitOptionPort implements KitOptionPort {
	private final Map<Long, List<KitOption>> optionsByRace = new ConcurrentHashMap<>();

	@Override
	public List<KitOption> findKitOptions(long raceId) {
		return optionsByRace.getOrDefault(raceId, List.of());
	}

	public void add(long raceId, long kitOptionId, String name, String value, Integer stock) {
		optionsByRace.computeIfAbsent(raceId, id -> new ArrayList<>())
			.add(new KitOption(kitOptionId, name, value, stock));
	}

	public void clear() {
		optionsByRace.clear();
	}
}
