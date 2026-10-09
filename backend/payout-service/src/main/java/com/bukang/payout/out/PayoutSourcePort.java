package com.bukang.payout.out;

import java.time.LocalDate;
import java.util.List;

/**
 * 정산이 주문 서비스에서 받아야 하는 데이터의 계약 (포트)
 * 주문 서비스가 생기면 이 인터페이스를 구현한 어댑터(HTTP 호출 또는 Kafka 복제본)를 붙인다. 배치 코드는 바뀌지 않는다
 */
@FunctionalInterface
public interface PayoutSourcePort {
	/**
	 * raceDate에 열린 대회에서 결제되고 취소되지 않은 참가권을 돌려준다.
	 * 정산 대상일 전에 취소된 참가권은 넣지 않는다 (정산 전 취소, payout.md 1장 정산 대상일 5번)
	 */
	List<SoldEntry> findSoldEntries(LocalDate raceDate);
}
