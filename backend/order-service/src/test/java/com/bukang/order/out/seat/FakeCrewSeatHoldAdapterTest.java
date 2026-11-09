package com.bukang.order.out.seat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.order.out.seat.CrewSeatHoldPort.CrewSeatHoldResult;
import com.bukang.order.out.seat.CrewSeatHoldPort.Seat;
import com.bukang.order.out.seat.CrewSeatHoldPort.Shortage;

class FakeCrewSeatHoldAdapterTest {
	private static final long COURSE_10K = 1L;
	private static final long COURSE_HALF = 2L;
	private static final long CREW_ID = 7L;
	private static final long RACE_ID = 1L;

	private static final LocalDateTime DUE = LocalDateTime.now().plusDays(1);

	private final FakeSeatHoldAdapter seatHoldAdapter = new FakeSeatHoldAdapter();
	private final FakeCrewSeatHoldAdapter adapter = new FakeCrewSeatHoldAdapter(seatHoldAdapter);
	private long nextParticipationId = 1;

	@Test
	@DisplayName("남은 크루 자리 5석에 4명 파티는 잡히고, 이어서 온 2명 파티는 한 석도 잡지 않는다 (11/03 결정)")
	void allOrNothing() {
		adapter.holdAll(100L, CREW_ID, RACE_ID, DUE, seats(COURSE_10K, FakeCrewSeatHoldAdapter.CREW_SEATS_PER_COURSE - 5));

		CrewSeatHoldResult four = adapter.holdAll(101L, CREW_ID, RACE_ID, DUE, seats(COURSE_10K, 4));
		CrewSeatHoldResult two = adapter.holdAll(102L, CREW_ID, RACE_ID, DUE, seats(COURSE_10K, 2));

		assertThat(four.held()).isTrue();
		assertThat(four.expiresAt()).isNotNull();
		assertThat(two.held()).isFalse();
		assertThat(two.shortages()).isEqualTo(Map.of(COURSE_10K, new Shortage(2, 1)));
		// 남은 1석은 그대로라 1명 파티는 잡힌다
		assertThat(adapter.holdAll(103L, CREW_ID, RACE_ID, DUE, seats(COURSE_10K, 1)).held()).isTrue();
	}

	@Test
	@DisplayName("종목이 섞인 파티는 한 종목이라도 모자라면 다른 종목 자리도 잡지 않는다")
	void mixedCoursesRollBack() {
		adapter.holdAll(100L, CREW_ID, RACE_ID, DUE, seats(COURSE_HALF, FakeCrewSeatHoldAdapter.CREW_SEATS_PER_COURSE));
		List<Seat> mixed = new ArrayList<>(seats(COURSE_10K, 3));
		mixed.addAll(seats(COURSE_HALF, 1));

		CrewSeatHoldResult result = adapter.holdAll(101L, CREW_ID, RACE_ID, DUE, mixed);

		assertThat(result.held()).isFalse();
		assertThat(result.shortages()).containsOnlyKeys(COURSE_HALF);
		// 10K 자리는 줄지 않았다
		assertThat(adapter.holdAll(102L, CREW_ID, RACE_ID, DUE,
			seats(COURSE_10K, FakeCrewSeatHoldAdapter.CREW_SEATS_PER_COURSE)).held())
			.isTrue();
	}

	@Test
	@DisplayName("같은 파티 ID로 다시 부르면 새로 잡지 않고 처음 결과를 돌려준다")
	void idempotentByPartyId() {
		CrewSeatHoldResult first = adapter.holdAll(100L, CREW_ID, RACE_ID, DUE, seats(COURSE_10K, 3));
		CrewSeatHoldResult again = adapter.holdAll(100L, CREW_ID, RACE_ID, DUE, seats(COURSE_10K, 3));

		assertThat(again).isEqualTo(first);
		// 3석만 줄었으므로 나머지 17석을 다른 파티가 모두 잡을 수 있다
		List<Seat> rest = seats(COURSE_10K, FakeCrewSeatHoldAdapter.CREW_SEATS_PER_COURSE - 3);
		assertThat(adapter.holdAll(101L, CREW_ID, RACE_ID, DUE, rest).held()).isTrue();
	}

	private List<Seat> seats(long raceCourseId, int count) {
		return LongStream.range(0, count).mapToObj(i -> new Seat(nextParticipationId++, raceCourseId)).toList();
	}

	@Test
	@DisplayName("잡은 크루 자리는 기한 안에 개인 자리처럼 판매할 수 있다 (파티원 결제, BUK-38)")
	void sellCrewSeat() {
		List<Seat> held = seats(COURSE_10K, 2);
		adapter.holdAll(100L, CREW_ID, RACE_ID, DUE, held);

		assertThat(seatHoldAdapter.sell(held.getFirst().participationId())).isEqualTo(SeatSellResult.SOLD);
		assertThat(seatHoldAdapter.sell(9999L)).isEqualTo(SeatSellResult.EXPIRED);
	}
}
