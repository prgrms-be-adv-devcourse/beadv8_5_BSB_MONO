package com.bukang.payout.out;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 임시 어댑터: 주문 서비스가 아직 없어 정산할 참가권을 빈 목록으로 돌려준다
 * 주문 서비스와 연결할 때(HTTP 호출 또는 Kafka 복제본) 이 클래스를 진짜 어댑터로 바꾼다
 */
@Slf4j
@Component
public class NoOrderSourceAdapter implements PayoutSourcePort {
	@Override
	public List<SoldEntry> findSoldEntries(LocalDate raceDate) {
		log.warn("주문 서비스 연결 전이라 정산할 참가권이 없습니다. raceDate={}", raceDate);
		return List.of();
	}
}
