package com.bukang.order.out.seat;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.bukang.order.domain.exception.ParticipationNotPayableException;

import lombok.extern.slf4j.Slf4j;

/**
 * 가짜 어댑터: 상품 API가 생기기 전까지, 종목마다 크루 전용 자리 20석을 메모리에 두고 파티 단위로 한꺼번에 잡는다 (BUK-35)
 * 계약(전부 아니면 실패, 파티 ID 멱등)은 CrewSeatHoldPort 그대로 지킨다. 앱을 다시 켜면 처음으로 돌아간다
 * 잡은 자리는 가짜 자리 어댑터(FakeSeatHoldAdapter)에도 남겨, 파티원 결제의 자리 판매(SeatHoldPort.sell)가 성공하게 한다 (BUK-38)
 * order.clients.product.mode=fake(기본값)일 때만 쓴다. http면 HttpCrewSeatHoldAdapter가 실제 상품을 부른다
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "order.clients.product", name = "mode", havingValue = "fake", matchIfMissing = true)
public class FakeCrewSeatHoldAdapter implements CrewSeatHoldPort {
	static final int CREW_SEATS_PER_COURSE = 20;
	private final FakeSeatHoldAdapter seatHoldAdapter;
	private final Map<Long, Integer> remainingSeats = new HashMap<>();
	private final Map<Long, CrewSeatHoldResult> results = new HashMap<>();
	private final Map<Long, List<Seat>> heldSeats = new HashMap<>();

	public FakeCrewSeatHoldAdapter(FakeSeatHoldAdapter seatHoldAdapter) {
		this.seatHoldAdapter = seatHoldAdapter;
		log.warn("크루 자리 확보는 가짜 응답(FakeCrewSeatHoldAdapter)을 씁니다. 종목마다 크루 자리 {}석", CREW_SEATS_PER_COURSE);
	}

	// 확인과 차감을 한 번에 해야 두 파티가 같은 자리를 나눠 갖지 않는다 (B의 종목별 조건부 UPDATE + 한 트랜잭션 흉내)
	@Override
	public synchronized CrewSeatHoldResult holdAll(long partyId, long crewId, long raceId, LocalDateTime expiresAt,
		List<Seat> seats) {
		CrewSeatHoldResult first = results.get(partyId);
		if (first != null) {
			// 상품처럼 자리 목록이 처음과 다르면 처음 결과 대신 409로 거절한다 (CrewSeatHoldPort 계약)
			if (!Set.copyOf(heldSeats.get(partyId)).equals(Set.copyOf(seats))) {
				throw new ParticipationNotPayableException("이미 다른 자리 목록으로 확보한 파티입니다.");
			}
			return first;
		}
		Map<Long, Integer> needed = seats.stream()
			.collect(Collectors.groupingBy(Seat::raceCourseId, Collectors.summingInt(seat -> 1)));
		Map<Long, Shortage> shortages = needed.entrySet().stream()
			.filter(entry -> remaining(entry.getKey()) < entry.getValue())
			.collect(Collectors.toMap(Map.Entry::getKey,
				entry -> new Shortage(entry.getValue(), remaining(entry.getKey()))));
		if (!shortages.isEmpty()) {
			// 실패한 결과는 기억하지 않는다. 다른 파티가 취소해 자리가 나면 같은 파티가 다시 신청할 수 있다
			return CrewSeatHoldResult.shortOf(shortages);
		}
		needed.forEach((courseId, count) -> remainingSeats.put(courseId, remaining(courseId) - count));
		seats.forEach(seat -> seatHoldAdapter.holdCrewSeat(seat.participationId(), seat.raceCourseId(), expiresAt));
		CrewSeatHoldResult held = CrewSeatHoldResult.held(expiresAt);
		results.put(partyId, held);
		heldSeats.put(partyId, List.copyOf(seats));
		log.debug("가짜 크루 자리 확보 partyId={}, seats={}", partyId, needed);
		return held;
	}

	private int remaining(long raceCourseId) {
		return remainingSeats.getOrDefault(raceCourseId, CREW_SEATS_PER_COURSE);
	}
}
