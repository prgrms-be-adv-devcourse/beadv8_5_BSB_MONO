package com.bukang.order.in;

import java.time.LocalDateTime;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bukang.order.app.OrderExpiryUseCase;

import lombok.RequiredArgsConstructor;

/**
 * 1분마다 결제 기한이 지난 결제 대기 건을 정리한다 (PRO-42)
 * 테스트에서는 등록하지 않는다. 테스트는 OrderExpiryUseCase에 시각을 넣어 직접 부른다
 */
@Profile("!test")
@Component
@RequiredArgsConstructor
public class OrderExpiryScheduler {
	private final OrderExpiryUseCase orderExpiryUseCase;

	@Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
	public void expireOverdue() {
		LocalDateTime now = LocalDateTime.now();
		try {
			orderExpiryUseCase.expireOverdue(now);
		} finally {
			// 주문 만료가 DB 오류로 멈춰도 주문 없이 남은 자리는 따로 푼다
			orderExpiryUseCase.releaseAbandonedHolds(now);
		}
	}
}
