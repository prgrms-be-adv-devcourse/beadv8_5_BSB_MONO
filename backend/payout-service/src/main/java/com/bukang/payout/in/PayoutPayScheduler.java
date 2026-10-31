package com.bukang.payout.in;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bukang.payout.app.PayoutClaimUseCase;
import com.bukang.payout.app.PayoutPayUseCase;

import lombok.RequiredArgsConstructor;

/**
 * 매일 새벽 지급 예정일이 된 정산 내역을 지급 완료로 바꾼다
 * 매일 돌린다: 1차는 환불 마감일 + 2일, 최종은 대회일 + 2일이 지급일이라(주말에도 지급, 10/28 결정)
 * 날마다 지급할 건이 생길 수 있고, 실행을 놓친 날은 다음 실행에서 따라잡는다
 */
@Component
@RequiredArgsConstructor
public class PayoutPayScheduler {
	// 서버 시간대와 상관없이 한국 날짜로 지급일을 판단한다
	private static final String ZONE = "Asia/Seoul";

	private final PayoutPayUseCase payoutPayUseCase;
	private final PayoutClaimUseCase payoutClaimUseCase;

	// 정산 생성 배치(03:00) 뒤에 돈다. 정산 내역은 지급 예정일 전날 만들어지므로 같은 날 만든 건은 이 배치에 걸리지 않는다
	@Scheduled(cron = "0 0 4 * * *", zone = ZONE)
	public void payPayouts() {
		LocalDateTime now = LocalDateTime.now(ZoneId.of(ZONE));
		payoutPayUseCase.run(LocalDate.from(now), now);
		// 회수 청구 · 알림도 같은 배치에서 한다 (선 환불 후 회수). 지급은 실패해도 예외를 던지지 않아 청구가 막히지 않는다
		payoutClaimUseCase.run(LocalDate.from(now), now);
	}
}
