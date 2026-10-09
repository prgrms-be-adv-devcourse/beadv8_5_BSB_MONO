package com.bukang.payout.in;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bukang.payout.app.PayoutCreateUseCase;

import lombok.RequiredArgsConstructor;

/**
 * 매일 새벽 정산 대상일이 된 대회의 정산 내역을 만든다 (payout.md 2-6)
 */
@Component
@RequiredArgsConstructor
public class PayoutCreateScheduler {
	// 서버 시간대와 상관없이 한국 날짜로 정산 대상일을 판단한다
	private static final String ZONE = "Asia/Seoul";

	private final PayoutCreateUseCase payoutCreateUseCase;

	@Scheduled(cron = "0 0 3 * * *", zone = ZONE)
	public void createPayouts() {
		LocalDateTime now = LocalDateTime.now(ZoneId.of(ZONE));
		payoutCreateUseCase.run(LocalDate.from(now), now);
	}
}
