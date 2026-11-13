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
 * 매일 새벽 지급 예정일이 된 정산 내역을 지급 완료로 바꾸고, 이어서 회수 청구 · 알림을 한다
 * 지급일은 매달 20일(주말이어도 그날)이지만 매일 돌린다. 보류를 풀면 해제일 다음 날 지급하므로(10/28 결정)
 * 날마다 지급할 건이 생길 수 있고, 실행을 놓친 날은 다음 실행에서 따라잡는다
 * 잠금 · 실행 기록은 PayoutBatchUseCase가 맡는다 (BUK-167). 다른 서버나 관리자가 이미 돌리고 있으면 건너뛴다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutPayScheduler {
	// 서버 시간대와 상관없이 한국 날짜로 지급일을 판단한다
	private static final String ZONE = "Asia/Seoul";

	private final PayoutBatchUseCase payoutBatchUseCase;

	// 정산 생성 배치(03:00) 뒤에 돈다. 정산 내역은 지급 예정일 전날 만들어지므로 같은 날 만든 건은 이 배치에 걸리지 않는다
	@Scheduled(cron = "0 0 4 * * *", zone = ZONE)
	public void payPayouts() {
		LocalDateTime now = LocalDateTime.now(ZoneId.of(ZONE));
		try {
			payoutBatchUseCase.runPay(PayoutBatchTrigger.스케줄, null, LocalDate.from(now), now);
		} catch (PayoutBatchRunningException exception) {
			log.info("정산 지급 배치가 이미 돌고 있어 이번 실행은 건너뜁니다.");
		}
	}
}
