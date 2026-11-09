package com.bukang.payout.in;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bukang.payout.app.PayoutCreateUseCase;
import com.bukang.payout.app.PayoutReconcileUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 매일 새벽 정산 생성일이 된 대회의 정산 내역을 만들고, 이어서 주문 판매와 대사한다 (payout.md 2-6, BUK-67)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutCreateScheduler {
	// 서버 시간대와 상관없이 한국 날짜로 정산 생성일을 판단한다
	private static final String ZONE = "Asia/Seoul";

	private final PayoutCreateUseCase payoutCreateUseCase;
	private final PayoutReconcileUseCase payoutReconcileUseCase;

	@Scheduled(cron = "0 0 3 * * *", zone = ZONE)
	public void createPayouts() {
		LocalDateTime now = LocalDateTime.now(ZoneId.of(ZONE));
		LocalDate today = LocalDate.from(now);
		payoutCreateUseCase.run(today, now);
		// 대사는 같은 실행 시작 시각(now)으로 이번 실행에서 만든 정산을 찾는다. 대사가 실패해도 정산 생성 결과는
		// 이미 커밋됐으므로 바뀌지 않는다 (AC-67-10)
		try {
			payoutReconcileUseCase.run(today, now);
		} catch (RuntimeException exception) {
			log.error("정산 대사를 마치지 못했습니다. today={}", today, exception);
		}
	}
}
