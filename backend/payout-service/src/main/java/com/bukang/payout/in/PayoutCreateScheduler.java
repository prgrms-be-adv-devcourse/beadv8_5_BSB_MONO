package com.bukang.payout.in;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bukang.payout.app.PayoutBatchUseCase;
import com.bukang.payout.domain.PayoutBatchTrigger;
import com.bukang.payout.domain.exception.PayoutBatchRunningException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 매일 새벽 정산 생성일이 된 대회의 정산 내역을 만들고, 이어서 주문 판매와 대사한다 (payout.md 2-6, BUK-67)
 * 잠금 · 실행 기록은 PayoutBatchUseCase가 맡는다 (BUK-167). 다른 서버나 관리자가 이미 돌리고 있으면 건너뛴다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutCreateScheduler {
	// 서버 시간대와 상관없이 한국 날짜로 정산 생성일을 판단한다
	private static final String ZONE = "Asia/Seoul";

	private final PayoutBatchUseCase payoutBatchUseCase;

	@Scheduled(cron = "0 0 3 * * *", zone = ZONE)
	public void createPayouts() {
		LocalDateTime now = LocalDateTime.now(ZoneId.of(ZONE));
		try {
			payoutBatchUseCase.runCreate(PayoutBatchTrigger.스케줄, null, LocalDate.from(now), now);
		} catch (PayoutBatchRunningException exception) {
			log.info("정산 생성 배치가 이미 돌고 있어 이번 실행은 건너뜁니다.");
		}
	}
}
