package com.bukang.member.recordcert.out;

import java.util.Optional;

/**
 * 기록증 제출 때 order-service에 참가권(주문 상품) 하나를 묻는다 (BUK-274, order 내부 API는 BUK-271)
 * 없는 참가권이면 빈 값, 묻지 못하면 RecordCertCheckUnavailableException(503)을 던진다
 */
public interface TicketPort {
	Optional<Ticket> find(long orderItemId);

	/**
	 * @param buyerId 참가권을 산 회원 ID
	 * @param raceId 주문 당시 대회 ID
	 * @param raceCourseId 주문 당시 종목 ID
	 * @param status order의 주문 상품 상태 이름 (ORDERED · PAID · CANCELING · CANCELED)
	 */
	record Ticket(long buyerId, long raceId, long raceCourseId, String status) {
		private static final String PAID = "PAID";

		public boolean isPaid() {
			return PAID.equals(status);
		}
	}
}
