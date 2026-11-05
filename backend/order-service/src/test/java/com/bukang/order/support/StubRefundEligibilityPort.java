package com.bukang.order.support;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.bukang.order.out.payout.RefundEligibilityPort;

/**
 * 테스트용 환불 가능 여부. 기본은 환불 가능이고, 테스트가 정산이 시작된 상황을 만들 수 있다
 */
public class StubRefundEligibilityPort implements RefundEligibilityPort {
	private volatile boolean refundable = true;
	// 정산 생성일이 이미 지나 정산이 시작된 대회 날짜들
	private final Set<LocalDate> settledRaceDates = ConcurrentHashMap.newKeySet();
	private final List<LocalDate> askedRaceDates = new CopyOnWriteArrayList<>();

	// 물어볼 때 DB 트랜잭션 안이었는지 (잠금을 쥔 채 다른 서비스를 부르지 않는지 확인할 때 쓴다)
	private final List<Boolean> askedInTransaction = new CopyOnWriteArrayList<>();

	@Override
	public boolean isRefundable(LocalDate raceDate) {
		askedRaceDates.add(raceDate);
		askedInTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
		return refundable && !settledRaceDates.contains(raceDate);
	}

	// 이 대회 날짜는 정산이 시작된 것으로 답한다 (정산 서비스의 "정산 생성일 지남")
	public void settle(LocalDate raceDate) {
		settledRaceDates.add(raceDate);
	}

	public List<Boolean> askedInTransaction() {
		return askedInTransaction;
	}

	// 물어본 대회 날짜들 (어느 날짜로 정산 생성일을 따졌는지 확인할 때 쓴다)
	public List<LocalDate> askedRaceDates() {
		return askedRaceDates;
	}

	public void setRefundable(boolean refundable) {
		this.refundable = refundable;
	}

	public void clear() {
		refundable = true;
		settledRaceDates.clear();
		askedRaceDates.clear();
		askedInTransaction.clear();
	}
}
