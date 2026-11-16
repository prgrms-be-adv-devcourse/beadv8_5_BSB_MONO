package com.bukang.board.domain;

/**
 * 아웃박스 행(BoardOutboxKafkaEvent)의 상태: 미발행(PENDING) → 발행 완료(SENT) 또는 발행 실패(SEND_FAILED).
 * 메시지를 브로커에 보냈는지만 기록한다. 받는 쪽이 처리했는지는 받는 쪽 몫이다.
 * 발행 실패는 재시도 횟수를 다 써서 더는 보내지 않는 상태다. 원인을 고친 뒤 PENDING으로 되돌리면 다시 보낸다.
 */
public enum OutboxStatus {
	PENDING,
	SENT,
	SEND_FAILED
}
