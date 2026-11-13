package com.bukang.cash.in;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bukang.cash.app.withdrawal.CashCardCancelDeadlineNoticeUseCase;

import lombok.RequiredArgsConstructor;

/**
 * 매일 09:00에 PG 결제취소 기한이 3일·1일 남은 회원에게 알림을 보낸다.
 * 테스트에서는 등록하지 않는다. 테스트는 CashCardCancelDeadlineNoticeUseCase에 시각을 넣어 직접 부른다
 */
@Profile("!test")
@Component
@RequiredArgsConstructor
public class CashCardCancelDeadlineNoticeScheduler {
	// 서버 시간대와 상관없이 한국 시각으로 돈다
	private static final String ZONE = "Asia/Seoul";

	private final CashCardCancelDeadlineNoticeUseCase cashCardCancelDeadlineNoticeUseCase;

	@Scheduled(cron = "0 0 9 * * *", zone = ZONE)
	public void notifyDeadlines() {
		// 기한과 알림 시각이 모두 한국 시간 기준이라 한국 시각을 넣는다
		cashCardCancelDeadlineNoticeUseCase.notifyDeadlines(LocalDateTime.now(ZoneId.of(ZONE)));
	}
}
