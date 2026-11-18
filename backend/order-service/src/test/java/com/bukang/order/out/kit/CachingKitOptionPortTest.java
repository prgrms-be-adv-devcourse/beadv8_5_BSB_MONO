package com.bukang.order.out.kit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.order.domain.exception.ExternalServiceUnavailableException;
import com.bukang.order.out.kit.KitOptionPort.KitOption;

class CachingKitOptionPortTest {
	private static final KitOption SHIRT_M = new KitOption(11, "T-shirt", "M", 30);

	private final AtomicLong now = new AtomicLong();
	private final Deque<Optional<List<KitOption>>> answers = new ArrayDeque<>();
	private int calls;
	private final CachingKitOptionPort port = new CachingKitOptionPort(raceId -> {
		calls++;
		Optional<List<KitOption>> answer = answers.poll();
		if (answer == null) {
			throw new ExternalServiceUnavailableException("상품 서비스 장애");
		}
		return answer;
	}, Duration.ofSeconds(5), now::get);

	@Test
	@DisplayName("같은 대회는 기억 시간 안에 다시 부르지 않고, 지나면 다시 부른다")
	void cachesUntilTtl() {
		answers.add(Optional.of(List.of(SHIRT_M)));
		answers.add(Optional.of(List.of()));

		assertThat(port.findKitOptions(2)).contains(List.of(SHIRT_M));
		now.addAndGet(Duration.ofMillis(4999).toNanos());
		assertThat(port.findKitOptions(2)).contains(List.of(SHIRT_M));
		assertThat(calls).isEqualTo(1);

		now.addAndGet(Duration.ofMillis(1).toNanos());
		assertThat(port.findKitOptions(2)).contains(List.of());
		assertThat(calls).isEqualTo(2);
	}

	@Test
	@DisplayName("404(빈 값)와 오류는 기억하지 않아 다음 요청이 다시 묻는다")
	void doesNotCacheNotFoundOrError() {
		answers.add(Optional.empty());
		assertThat(port.findKitOptions(2)).isEmpty();
		assertThatThrownBy(() -> port.findKitOptions(2)).isInstanceOf(ExternalServiceUnavailableException.class);
		answers.add(Optional.of(List.of(SHIRT_M)));
		assertThat(port.findKitOptions(2)).contains(List.of(SHIRT_M));
		assertThat(calls).isEqualTo(3);
	}

	@Test
	@DisplayName("대회마다 따로 기억한다")
	void perRace() {
		answers.add(Optional.of(List.of(SHIRT_M)));
		answers.add(Optional.of(List.of()));

		assertThat(port.findKitOptions(2)).contains(List.of(SHIRT_M));
		assertThat(port.findKitOptions(3)).contains(List.of());
		assertThat(calls).isEqualTo(2);
	}

	@Test
	@DisplayName("늦게 끝난 옛 응답은 그사이 들어온 새 응답을 덮지 않는다")
	void olderResponseDoesNotOverwrite() {
		KitOption shirtL = new KitOption(12, "T-shirt", "L", 10);
		AtomicLong clock = new AtomicLong();
		CachingKitOptionPort[] holder = new CachingKitOptionPort[1];
		holder[0] = new CachingKitOptionPort(raceId -> {
			if (clock.get() == 0) {
				// 첫 요청이 상품을 기다리는 사이 1초 뒤 다른 요청이 새 목록을 받아 넣는다
				clock.set(Duration.ofSeconds(1).toNanos());
				holder[0].findKitOptions(raceId);
				return Optional.of(List.of(SHIRT_M));
			}
			return Optional.of(List.of(shirtL));
		}, Duration.ofSeconds(5), clock::get);

		assertThat(holder[0].findKitOptions(2)).contains(List.of(SHIRT_M));
		assertThat(holder[0].findKitOptions(2)).contains(List.of(shirtL));
	}
}
