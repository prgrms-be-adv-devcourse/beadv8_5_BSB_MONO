package com.bukang.order.in;

import java.time.LocalDateTime;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.bukang.order.app.PartyPaymentDeadlineUseCase;

import lombok.RequiredArgsConstructor;

/**
 * 1분마다 파티원 결제 마감 알림과 결제 기간 마감을 처리한다 (BUK-38)
 * 결제 기한은 파티마다 다르므로(신청 + 24시간 등) 하루 한 번이 아니라 자주 본다. 결제 판정은 주문 기한이 하므로 몇 분 늦어도 된다
 * 테스트에서는 등록하지 않는다. 테스트는 PartyPaymentDeadlineUseCase에 시각을 넣어 직접 부른다
 */
@Profile("!test")
@Component
@RequiredArgsConstructor
public class PartyPaymentScheduler {
	private final PartyPaymentDeadlineUseCase partyPaymentDeadlineUseCase;

	@SchedulerLock(name = "order-party-payment", lockAtMostFor = "PT10M")
	@Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
	public void run() {
		LocalDateTime now = LocalDateTime.now();
		partyPaymentDeadlineUseCase.remindDue(now);
		partyPaymentDeadlineUseCase.finishDue(now);
	}
}
