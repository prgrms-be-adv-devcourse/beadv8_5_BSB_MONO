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
 * 매일 05:00 지난달 수수료 세금계산서를 발행한다 (BUK-246)
 * 1일에 지난달 치를 처음 발행하고, 그 뒤로는 실패한 건만 다시 시도한다. 매일 돌아 실행을 놓친 날도 따라잡는다
 * 지급 배치(04:00) 뒤에 돌아 1일 새벽에 지급 완료된 건이 섞이지 않는다 (지급 완료월이 이번 달이라 대상이 아니다)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutTaxInvoiceScheduler {
	private static final String ZONE = "Asia/Seoul";

	private final PayoutBatchUseCase payoutBatchUseCase;

	@Scheduled(cron = "0 0 5 * * *", zone = ZONE)
	public void issueTaxInvoices() {
		LocalDateTime now = LocalDateTime.now(ZoneId.of(ZONE));
		try {
			payoutBatchUseCase.runTaxInvoice(PayoutBatchTrigger.스케줄, null, LocalDate.from(now), now);
		} catch (PayoutBatchRunningException exception) {
			log.info("세금계산서 배치가 이미 돌고 있어 이번 실행은 건너뜁니다.");
		}
	}
}
