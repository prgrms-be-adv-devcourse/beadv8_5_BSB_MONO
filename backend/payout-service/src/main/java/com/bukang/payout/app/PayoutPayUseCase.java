package com.bukang.payout.app;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.payout.domain.PayoutStatus;
import com.bukang.payout.out.PayoutRepository;

import lombok.RequiredArgsConstructor;

/**
 * 지급 배치: 지급 예정일이 된 정산 예정(CALCULATED) 정산 내역을 지급 완료(PAID)로 바꾼다 (payout.md 2-6)
 * 보류(HELD)는 상태가 달라 고르지 않는다. 보류가 풀리면 PRO-26이 지급 예정일을 다시 정하고 CALCULATED로 돌린다
 */
@Service
@RequiredArgsConstructor
public class PayoutPayUseCase {
	private final PayoutRepository payoutRepository;

	@Transactional
	public void run(LocalDate today, LocalDateTime now) {
		payoutRepository.findByStatusAndScheduledPayDateLessThanEqual(PayoutStatus.CALCULATED, today)
			.forEach(payout -> payout.pay(now));
	}
}
