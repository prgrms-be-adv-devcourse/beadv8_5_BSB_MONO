package com.bukang.order.in;

import java.time.LocalDateTime;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bukang.order.app.OrderPayUseCase;
import com.bukang.order.out.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * C안 시제품(측정 브랜치): 결제 중으로 30초 넘게 남은 주문(응답을 잃은 결제)을 15초마다 다시 확인해 확정하거나 되돌린다
 * shortcut: 서버 여러 대·분산 락·건수 상한·알림은 없다. 실제 C안에서는 만료 정리처럼 락과 재시도 한도를 둔다
 */
@Slf4j
@Profile("!test")
@Component
@RequiredArgsConstructor
public class OrderPayRecheckScheduler {
	private static final long STALE_SECONDS = 30;

	private final OrderRepository orderRepository;
	private final OrderPayUseCase orderPayUseCase;

	@Scheduled(fixedDelay = 15_000, initialDelay = 15_000)
	public void recheck() {
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime before = now.minusSeconds(STALE_SECONDS);
		for (long orderId : orderRepository.findStalePayingIds(before, PageRequest.of(0, 200))) {
			try {
				orderPayUseCase.recheck(orderId, now);
			} catch (RuntimeException exception) {
				log.warn("결제 중 주문을 다시 확인하지 못했습니다. orderId={}, cause={}", orderId, exception.toString());
			}
		}
	}
}
