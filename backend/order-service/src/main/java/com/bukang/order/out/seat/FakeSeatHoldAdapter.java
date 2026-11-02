package com.bukang.order.out.seat;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 가짜 어댑터: 상품 서비스를 띄우지 않고 개발할 때, 종목마다 정원 100석을 메모리에 두고 주문이 보낸 기한까지 자리를 내준다
 * 확보한 자리의 기한을 기억해 기한 안 판매까지 흉내 낸다. 복구·해제는 아웃박스 → Kafka로 상품 서비스에 가므로(PRO-270)
 * 여기서는 자리가 정원으로 돌아오지 않는다 (가짜 정원 100석이 줄기만 한다)
 * 앱을 다시 켜면 처음으로 돌아간다. order.clients.product.mode=fake(기본값)일 때만 쓰고, http면 HttpSeatHoldAdapter가 부른다
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "order.clients.product", name = "mode", havingValue = "fake", matchIfMissing = true)
public class FakeSeatHoldAdapter implements SeatHoldPort {
	private static final int SEATS_PER_COURSE = 100;

	private final Map<Long, Integer> remainingSeats = new ConcurrentHashMap<>();
	private final Map<Long, Hold> holds = new ConcurrentHashMap<>();
	private final Set<Long> sold = ConcurrentHashMap.newKeySet();

	// 호출마다 경고를 남기면 로그가 묻히므로, 앱을 켤 때 한 번만 알린다
	public FakeSeatHoldAdapter() {
		log.warn("자리 확보는 가짜 응답(FakeSeatHoldAdapter)을 씁니다. 실제 상품 서비스는 order.clients.product.mode=http");
	}

	@Override
	public SeatHoldResult hold(long raceCourseId, long participationId, LocalDateTime expiresAt) {
		// "남은 자리가 1 이상일 때만 줄인다"를 종목별로 원자적으로 처리한다 (B의 조건부 UPDATE 흉내)
		boolean[] held = {false};
		remainingSeats.compute(raceCourseId, (id, remaining) -> {
			int seats = remaining == null ? SEATS_PER_COURSE : remaining;
			if (seats <= 0) {
				return seats;
			}
			held[0] = true;
			return seats - 1;
		});
		log.debug("가짜 자리 확보 raceCourseId={}, participationId={}, held={}", raceCourseId, participationId, held[0]);
		if (!held[0]) {
			return SeatHoldResult.soldOut();
		}
		holds.put(participationId, new Hold(raceCourseId, expiresAt));
		return SeatHoldResult.held(expiresAt);
	}

	@Override
	public SeatSellResult sell(long participationId) {
		// 이미 판매한 자리는 기한과 상관없이 SOLD (같은 주문 재결제, SeatHoldPort.sell)
		if (sold.contains(participationId)) {
			return SeatSellResult.SOLD;
		}
		Hold hold = holds.get(participationId);
		if (hold == null || !LocalDateTime.now().isBefore(hold.expiresAt())) {
			return SeatSellResult.EXPIRED;
		}
		holds.remove(participationId);
		sold.add(participationId);
		return SeatSellResult.SOLD;
	}

	private record Hold(long raceCourseId, LocalDateTime expiresAt) {
	}
}
