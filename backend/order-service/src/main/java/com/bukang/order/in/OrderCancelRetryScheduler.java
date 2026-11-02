package com.bukang.order.in;

import java.time.LocalDateTime;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bukang.order.app.OrderCancelRetryUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 1분마다 환불 결과를 몰라 취소 중으로 남은 사용자 취소를 다시 환불해 마무리한다 (BUK-9)
 * fixedDelay: 이전 실행이 끝나고 1분 뒤에 다시 실행한다. 예치금이 느려 한 번이 길어져도 실행이 겹치지 않는다
 * 앱이 뜬 뒤 90초 기다린다. 만료 정리·릴레이와 시작 시각이 겹치지 않게 할 뿐이고,
 * 시작한 지 1분이 안 된 취소 중을 건드리지 않는 것은 조회 조건(OrderCancelRetryUseCase.RETRY_AFTER)이 맡는다
 * 테스트에서는 등록하지 않는다. 테스트는 OrderCancelRetryUseCase에 시각을 넣어 직접 부른다
 */
@Slf4j
@Profile("!test")
@Component
@RequiredArgsConstructor
public class OrderCancelRetryScheduler {
	private final OrderCancelRetryUseCase orderCancelRetryUseCase;

	@Scheduled(fixedDelay = 60_000, initialDelay = 90_000)
	public void retry() {
		OrderCancelRetryUseCase.RetryResult result = orderCancelRetryUseCase.retry(LocalDateTime.now());
		if (result.completed() + result.reverted() + result.pending() > 0) {
			log.info("취소 재처리: 마무리 {}건, 결제 완료로 되돌림 {}건, 아직 모름 {}건",
				result.completed(), result.reverted(), result.pending());
		}
	}
}
