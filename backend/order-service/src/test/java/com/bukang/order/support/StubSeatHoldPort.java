package com.bukang.order.support;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.bukang.order.out.seat.SeatHoldPort;
import com.bukang.order.out.seat.SeatHoldResult;
import com.bukang.order.out.seat.SeatSellResult;

/**
 * 테스트용 자리 확보. 종목별 남은 자리를 정할 수 있고(정하지 않으면 무제한), 결제 기한은 주문이 보낸 값(대기열 입장 끝 시각)이다
 * 자리 복구·해제는 주문 아웃박스에 남으므로 SeatOutboxMessages로 확인한다
 */
public class StubSeatHoldPort implements SeatHoldPort {
	private final Map<Long, Integer> remainingSeats = new ConcurrentHashMap<>();
	private final AtomicInteger holdCalls = new AtomicInteger();
	private final Map<Long, LocalDateTime> expiresAt = new ConcurrentHashMap<>();
	private final Set<Long> sold = ConcurrentHashMap.newKeySet();
	// 자리 확보 때 예상하지 못한 오류(타임아웃 등)를 낼 종목
	private final Set<Long> failingCourses = ConcurrentHashMap.newKeySet();

	@Override
	public SeatHoldResult hold(long raceCourseId, long participationId, LocalDateTime requestedExpiresAt) {
		holdCalls.incrementAndGet();
		if (failingCourses.contains(raceCourseId)) {
			throw new IllegalStateException("상품 서비스 응답 없음 (테스트)");
		}
		boolean[] held = {true};
		remainingSeats.computeIfPresent(raceCourseId, (id, remaining) -> {
			if (remaining <= 0) {
				held[0] = false;
				return remaining;
			}
			return remaining - 1;
		});
		if (!held[0]) {
			return SeatHoldResult.soldOut();
		}
		expiresAt.put(participationId, requestedExpiresAt);
		return SeatHoldResult.held(requestedExpiresAt);
	}

	@Override
	public SeatSellResult sell(long participationId) {
		// 이미 판매한 자리는 기한과 상관없이 SOLD (SeatHoldPort.sell의 멱등 계약)
		if (sold.contains(participationId)) {
			return SeatSellResult.SOLD;
		}
		LocalDateTime due = expiresAt.get(participationId);
		if (due == null || !LocalDateTime.now().isBefore(due)) {
			return SeatSellResult.EXPIRED;
		}
		sold.add(participationId);
		return SeatSellResult.SOLD;
	}

	// 상품 서비스 쪽 기한만 지나게 만든다 (주문 쪽 사본은 그대로). 결제 직전 만료 상황을 재현할 때 쓴다
	public void expire(long participationId) {
		expiresAt.put(participationId, LocalDateTime.now().minusSeconds(1));
	}

	// 판매(HELD → SOLD)까지 끝난 자리인지
	public boolean isSold(long participationId) {
		return sold.contains(participationId);
	}

	public void failHold(long raceCourseId) {
		failingCourses.add(raceCourseId);
	}

	public void setRemainingSeats(long raceCourseId, int seats) {
		remainingSeats.put(raceCourseId, seats);
	}

	public int holdCalls() {
		return holdCalls.get();
	}

	public void clear() {
		remainingSeats.clear();
		holdCalls.set(0);
		expiresAt.clear();
		sold.clear();
		failingCourses.clear();
	}
}
