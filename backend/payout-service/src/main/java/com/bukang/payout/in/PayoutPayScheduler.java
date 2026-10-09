package com.bukang.payout.in;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bukang.payout.app.PayoutPayUseCase;

import lombok.RequiredArgsConstructor;

/**
 * 매일 새벽 지급 예정일이 된 정산 내역을 지급 완료로 바꾼다 (payout.md 2-6)
 * 20일에만 돌리지 않고 매일 돌린다: 20일이 주말이면 다음 월요일에 잡히고, 실행을 놓친 날은 다음 실행에서 따라잡는다
 */
@Component
@RequiredArgsConstructor
public class PayoutPayScheduler {
	// 서버 시간대와 상관없이 한국 날짜로 지급일을 판단한다
	private static final String ZONE = "Asia/Seoul";

	private final PayoutPayUseCase payoutPayUseCase;

	// 정산 생성 배치(03:00) 뒤에 돈다. 같은 날 만든 정산 내역은 이번 달 분이라 순서가 결과를 바꾸지는 않는다
	@Scheduled(cron = "0 0 4 * * *", zone = ZONE)
	public void payPayouts() {
		LocalDateTime now = LocalDateTime.now(ZoneId.of(ZONE));
		payoutPayUseCase.run(LocalDate.from(now), now);
	}
}
