package com.bukang.order.out.seat;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.bukang.order.domain.exception.ExternalServiceUnavailableException;

/**
 * 상품의 크루 자리 한꺼번에 확보 API가 생기기 전까지, 실제 상품 서비스를 부르는 환경(운영)에서 파티 신청을 막는다 (BUK-35)
 * 가짜 어댑터는 메모리 자리라 운영에서 켜면 안 되고, 빈이 없으면 앱이 뜨지 않는다. 상품 API가 오면 HTTP 어댑터로 바꾼다
 */
@Component
@ConditionalOnProperty(prefix = "order.clients.product", name = "mode", havingValue = "http")
public class DisabledCrewSeatHoldAdapter implements CrewSeatHoldPort {
	static final String MESSAGE = "파티 신청을 아직 쓸 수 없습니다. 잠시 뒤 다시 시도해주세요.";

	@Override
	public CrewSeatHoldResult holdAll(long partyId, long crewId, LocalDateTime expiresAt, List<Seat> seats) {
		throw new ExternalServiceUnavailableException(MESSAGE);
	}
}
