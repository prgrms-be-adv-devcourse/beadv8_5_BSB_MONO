package com.bukang.order.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;
import com.bukang.common.shared.kafka.outbox.OutboxStatus;

// 발행 실패(SEND_FAILED)가 아닌 아웃박스 행을 되돌리려 함. 이미 보냈거나 아직 보내는 중이다 (BUK-180)
public class OutboxEventNotFailedException extends BusinessException {
	public static final String CODE = "ORDER_OUTBOX_INVALID_STATUS";

	public OutboxEventNotFailedException(long id, OutboxStatus status) {
		super(HttpStatus.CONFLICT, CODE, "아웃박스 메시지 " + id + "번은 " + label(status) + " 상태라 다시 보낼 수 없습니다.");
	}

	// common 상태에는 화면용 이름이 없어 여기서 붙인다 (BUK-207 전 주문 OutboxStatus의 label과 같은 말)
	private static String label(OutboxStatus status) {
		return switch (status) {
			case PENDING -> "미처리";
			case SENT -> "발행 완료";
			case SEND_FAILED -> "발행 실패";
		};
	}
}
