package com.bukang.order.out.payout;

import java.time.LocalDate;

/**
 * 정산 서비스(E)에 참가권을 환불할 수 있는지 묻는다 (GET /internal/v1/payout/refund-eligibility?raceDate=)
 * 정산 생성일(대회 다음 날)부터는 사유와 상관없이 환불할 수 없다. 환불 기한 판단은 주문 서비스가 따로 한다
 */
public interface RefundEligibilityPort {
	boolean isRefundable(LocalDate raceDate);
}
