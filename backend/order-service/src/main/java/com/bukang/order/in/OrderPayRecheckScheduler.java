package com.bukang.order.in;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bukang.common.global.exception.BusinessException;
import com.bukang.order.app.OrderPayUseCase;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.OrderRepository.PayingSummary;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 재확인 배치 (BUK-232). 15초마다, 결제 중이 된 지 30초 넘은 주문(결과를 모르는 결제)을 다시 맞춘다
 * 결제 나누기 스위치(order.pay.split.enabled)와 상관없이 돈다. 스위치를 끈 뒤에도 남은 결제 중을 끝내야 하기 때문이다
 * 여러 서버가 같은 주문을 함께 맞춰도 주문 잠금과 예치금·자리 판매의 멱등으로 한 번만 확정된다
 * shortcut: 분산 락이 없다. #474(ShedLock)가 머지되면 @SchedulerLock(name = "order-pay-recheck", lockAtMostFor = "PT2M")을 단다
 * 테스트에서는 등록하지 않는다. 테스트는 OrderPayUseCase.recheck에 시각을 넣어 직접 부른다
 */
@Slf4j
@Profile("!test")
@Component
@RequiredArgsConstructor
public class OrderPayRecheckScheduler {
	// 결제 중이 된 뒤 이만큼 지나야 다시 본다. 요청이 아직 예치금·상품을 부르는 중일 수 있다 (추천값(미확정), 호출 시간 제한 2초 + 여유)
	static final Duration STALE_AFTER = Duration.ofSeconds(30);
	// 한 번에 다시 볼 주문 수. 주문마다 예치금·상품을 2초까지 기다리므로, 한 실행이 길어도 2분 안에 끝나게 둔다 (추천값(미확정))
	static final int BATCH_SIZE = 30;

	private final OrderRepository orderRepository;
	private final OrderPayUseCase orderPayUseCase;

	@Scheduled(fixedDelay = 15_000, initialDelay = 15_000)
	public void recheck() {
		LocalDateTime now = LocalDateTime.now();
		List<Long> orderIds = orderRepository.findStalePayingIds(now.minus(STALE_AFTER), PageRequest.of(0, BATCH_SIZE));
		int paid = 0;
		for (long orderId : orderIds) {
			try {
				if (orderPayUseCase.recheck(orderId, now)) {
					paid++;
				}
			} catch (BusinessException exception) {
				// 잔액 부족·자리 기한 지남처럼 확실한 실패(되돌렸다)이거나, 확정 때 주문이 이미 끝나 있었다
				log.info("결제 중 주문이 결제 완료가 아닌 결과로 끝났습니다. orderId={}, cause={}", orderId, exception.getMessage());
			} catch (RuntimeException exception) {
				log.warn("결제 중 주문을 다시 확인하지 못했습니다. orderId={}, cause={}", orderId, exception.toString());
			}
		}
		// 지표: 결제 중 주문 수와 가장 오래 머문 시간 (shortcut: Micrometer가 없어 로그로 남긴다. actuator를 넣으면 Gauge로 바꾼다)
		PayingSummary summary = orderRepository.summarizePaying();
		if (!orderIds.isEmpty() || summary.getCount() > 0) {
			long oldestSeconds = summary.getOldestStartedAt() == null
				? 0 : Duration.between(summary.getOldestStartedAt(), now).toSeconds();
			log.info("결제 재확인: 다시 본 주문 {}건, 결제 완료 확인 {}건, 남은 결제 중 {}건, 가장 오래 머문 시간 {}초",
				orderIds.size(), paid, summary.getCount(), oldestSeconds);
		}
	}
}
