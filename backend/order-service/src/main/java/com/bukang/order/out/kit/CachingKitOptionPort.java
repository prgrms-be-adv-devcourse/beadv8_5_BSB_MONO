package com.bukang.order.out.kit;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 대회별 키트 옵션 목록을 잠깐(order.clients.product.kit-option-cache-ttl) 기억해, 결제마다 상품 HTTP를 부르지 않게 한다 (CRU-45)
 * 옵션과 준비 수량만 기억하고 판매 수는 기억하지 않는다. 초과 판매는 지금처럼 주문 DB의 판매 수 줄 잠금이 막는다
 * 받은 목록(빈 목록 포함)만 기억한다. 404(빈 값)는 취소된 대회인지 부르는 쪽이 매번 가려야 해서, 오류는 다음 요청이 다시 물어야 해서 기억하지 않는다
 * 판매자가 준비 수량을 바꾸면 길게는 기억 시간만큼 옛 값으로 판다. 지금도 상품 HTTP가 잠그기 전이라 같은 틈이 있다
 */
public class CachingKitOptionPort implements KitOptionPort {
	private final KitOptionPort delegate;
	private final long ttlNanos;
	private final LongSupplier nanoTime;
	// shortcut: 대회 수만큼만 쌓이고 지우지 않는다, 대회가 수천 개를 넘으면 Caffeine으로 바꾼다
	private final Map<Long, Entry> entries = new ConcurrentHashMap<>();

	public CachingKitOptionPort(KitOptionPort delegate, Duration ttl, LongSupplier nanoTime) {
		this.delegate = delegate;
		this.ttlNanos = ttl.toNanos();
		this.nanoTime = nanoTime;
	}

	@Override
	public Optional<List<KitOption>> findKitOptions(long raceId) {
		long now = nanoTime.getAsLong();
		Entry entry = entries.get(raceId);
		if (entry != null && now - entry.loadedAt() < ttlNanos) {
			return Optional.of(entry.options());
		}
		Optional<List<KitOption>> options = delegate.findKitOptions(raceId);
		// 늦게 끝난 옛 응답이 먼저 들어온 새 응답을 덮지 않게 더 늦게 시작한 쪽을 남긴다
		options.ifPresentOrElse(list -> entries.merge(raceId, new Entry(List.copyOf(list), now),
				(old, loaded) -> loaded.loadedAt() - old.loadedAt() >= 0 ? loaded : old),
			() -> entries.remove(raceId));
		return options;
	}

	private record Entry(List<KitOption> options, long loadedAt) {
	}
}
