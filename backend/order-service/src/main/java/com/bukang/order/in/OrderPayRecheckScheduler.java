package com.bukang.order.in;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.bukang.order.app.OrderPayRecheckUseCase;
import com.bukang.order.app.OrderPayRecheckUseCase.Run;
import com.bukang.order.out.OrderRepository;
import com.bukang.order.out.OrderRepository.PayingSummary;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 재확인 배치 (BUK-232). 15초마다, 결제 중이 된 지 30초 넘은 주문(결과를 모르는 결제)을 다시 맞춘다
 * 한 회차가 남은 결제 중을 다 볼 때까지 반복하되, 10초 상한·예치금 장애에서 멈춘다 (OrderPayRecheckUseCase)
 * 결제 나누기 스위치(order.pay.split.enabled)와 상관없이 돈다. 스위치를 끈 뒤에도 남은 결제 중을 끝내야 하기 때문이다
 * 여러 서버가 같은 주문을 함께 맞춰도 주문 잠금과 예치금·자리 판매의 멱등으로 한 번만 확정된다
 * 서버가 여러 대면 ShedLock(BUK-206)으로 한 서버만 돈다. 한 회차는 10초 상한이라 잠금은 2분이면 넉넉하다
 * 테스트에서는 등록하지 않는다. 테스트는 OrderPayRecheckUseCase.recheckStale에 시각을 넣어 직접 부른다
 */
@Slf4j
@Profile("!test")
@Component
@RequiredArgsConstructor
public class OrderPayRecheckScheduler {
	// 결제 중이 된 뒤 이만큼 지나야 다시 본다. 요청이 아직 예치금·상품을 부르는 중일 수 있다 (추천값(미확정), 호출 시간 제한 2초 + 여유)
	static final Duration STALE_AFTER = Duration.ofSeconds(30);
	// 한 회차의 시간 상한. 넘으면 남은 주문은 다음 회차(15초 뒤)에 본다 (추천값(미확정))
	static final Duration BUDGET = Duration.ofSeconds(10);

	private final OrderRepository orderRepository;
	private final OrderPayRecheckUseCase orderPayRecheckUseCase;

	@SchedulerLock(name = "order-pay-recheck", lockAtMostFor = "PT2M")
	@Scheduled(fixedDelay = 15_000, initialDelay = 15_000)
	public void recheck() {
		LocalDateTime now = LocalDateTime.now();
		Run run = orderPayRecheckUseCase.recheckStale(now, now.minus(STALE_AFTER), BUDGET);
		// 지표: 결제 중 주문 수와 가장 오래 머문 시간 (shortcut: Micrometer가 없어 로그로 남긴다. actuator를 넣으면 Gauge로 바꾼다)
		PayingSummary summary = orderRepository.summarizePaying();
		if (run.checked() > 0 || summary.getCount() > 0) {
			long oldestSeconds = summary.getOldestStartedAt() == null
				? 0 : Duration.between(summary.getOldestStartedAt(), now).toSeconds();
			log.info("결제 재확인: 다시 본 주문 {}건, 끝낸 주문 {}건, 멈춘 까닭 {}, 남은 결제 중 {}건, 가장 오래 머문 시간 {}초",
				run.checked(), run.finished(), run.stop(), summary.getCount(), oldestSeconds);
		}
	}
}
