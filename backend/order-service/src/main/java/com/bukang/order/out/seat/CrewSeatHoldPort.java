package com.bukang.order.out.seat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 파티 신청 때 상품 서비스(B)에 크루 전용 자리를 파티 단위로 한꺼번에 잡는다 (BUK-35, 11/03 결정)
 * 상품 API는 아직 없어 가짜 어댑터만 있다. 실제 API가 오면 HTTP 어댑터를 붙인다 (호빈님 요청: 크루 자리 한꺼번에 확보)
 *
 * 계약
 * - 전부 아니면 실패: 종목 하나라도 남은 크루 자리가 모자라면 한 석도 잡지 않는다. 예) 남은 5석에 4명 파티 → 4석,
 *   이어서 2명 파티 → 0석과 "2석 필요, 1석 남음". 나눠 잡지 않는다
 * - 멱등: 같은 파티 ID로 다시 부르면 새로 잡지 않고 처음 결과를 돌려준다 (응답을 못 받아 다시 불러도 자리가 두 배가 되지 않게)
 *   주의: 잡은 뒤 주문이 롤백되고 그 사이 파티원이 바뀌어 다시 부르면 seats가 처음과 다르다. 지금 계약은 처음 결과를 그대로 준다
 *   → 나간 사람 자리는 기한까지 묶이고 새로 온 사람 자리는 없다 (pending 57, 상품 API를 붙일 때 seats가 다르면 409로 맞춘다)
 * - 자리 1석 = 참가 신청 1건(participation_id). 잡은 뒤 판매는 SeatHoldPort.sell, 복구는 Kafka product.hold.restore로 지금과 같다
 */
public interface CrewSeatHoldPort {
	// crewId: 파티의 크루 ID. 상품이 자리 표(participation_hold.crew_id)에 남겨 결제율 미달을 계산한다 (11/06 결정, memberId는 보내지 않음)
	// expiresAt: 자리를 잡아 둘 끝 시각 = 파티원 결제 기한. 개인 자리처럼 주문이 정해 보낸다 (호빈님 요청서 1번)
	CrewSeatHoldResult holdAll(long partyId, long crewId, LocalDateTime expiresAt, List<Seat> seats);

	record Seat(long participationId, long raceCourseId) {
	}

	/**
	 * @param held      모두 잡았는지
	 * @param expiresAt 자리 기한 (보낸 결제 기한). 못 잡았으면 null
	 * @param shortages 못 잡았을 때 모자란 종목: 종목 ID → [필요한 수, 남은 수]. 잡았으면 비어 있다
	 */
	record CrewSeatHoldResult(boolean held, LocalDateTime expiresAt, Map<Long, Shortage> shortages) {
		public static CrewSeatHoldResult held(LocalDateTime expiresAt) {
			return new CrewSeatHoldResult(true, expiresAt, Map.of());
		}

		public static CrewSeatHoldResult shortOf(Map<Long, Shortage> shortages) {
			return new CrewSeatHoldResult(false, null, Map.copyOf(shortages));
		}
	}

	record Shortage(int needed, int remaining) {
	}
}
