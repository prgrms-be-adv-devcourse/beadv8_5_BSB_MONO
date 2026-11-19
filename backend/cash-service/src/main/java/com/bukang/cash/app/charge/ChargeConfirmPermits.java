package com.bukang.cash.app.charge;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.bukang.cash.domain.exception.charge.ChargeBusyException;

/**
 * 충전 확정을 동시에 정해진 수까지만 받는다 (격벽).
 * 충전은 토스 승인을 기다리는 동안 요청 쓰레드를 쥔다. 토스가 느려지면 충전이 쓰레드를 다 가져가
 * 토스를 부르지 않는 결제·잔액 조회까지 줄을 서므로, 넘치는 충전은 기다리지 않고 바로 거절해 나머지 몫을 남긴다
 * (2차 부하 측정 B-6, docs/measurements/2026-11-20-예치금-부하-2차-고칠것.md ①)
 */
@Component
class ChargeConfirmPermits {
	private final Semaphore permits;

	ChargeConfirmPermits(@Value("${cash.charge.confirm.max-concurrent}") int maxConcurrent) {
		if (maxConcurrent < 1) {
			throw new IllegalArgumentException("충전 확정 동시 개수는 1 이상이어야 합니다.");
		}
		this.permits = new Semaphore(maxConcurrent);
	}

	<T> T run(Supplier<T> work) {
		// 기다리면 그동안 쓰레드를 쥐므로 자리가 없으면 바로 거절한다
		if (!permits.tryAcquire()) {
			throw new ChargeBusyException();
		}
		try {
			return work.get();
		} finally {
			permits.release();
		}
	}
}
