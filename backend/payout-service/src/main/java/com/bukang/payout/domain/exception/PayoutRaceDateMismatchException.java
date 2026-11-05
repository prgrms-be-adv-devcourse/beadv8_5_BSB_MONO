package com.bukang.payout.domain.exception;

import java.time.LocalDate;
import java.util.Set;

// 참가권에 복사된 대회일이 상품의 대회일과 달라 정산 내역을 만들지 않는다 (정산 생성 배치 안에서만 쓰여 응답으로 나가지 않는다)
// 실패 기록표의 실패 종류를 예외 타입으로 나누기 위해 IllegalStateException과 구분한다 (BUK-60)
public class PayoutRaceDateMismatchException extends IllegalStateException {
	public PayoutRaceDateMismatchException(long raceId, LocalDate raceDate, Set<LocalDate> ticketRaceDates) {
		super("참가권의 대회일이 상품의 대회일과 다릅니다. raceId=%d, 상품=%s, 참가권=%s"
			.formatted(raceId, raceDate, ticketRaceDates));
	}
}
