package com.bukang.common.shared.kafka.outbox;

/**
 * 아웃박스 행의 상태 (BUK-200). DB에는 이름 그대로(문자열) 저장한다
 */
public enum OutboxStatus {
	// 보낼 차례
	PENDING,
	// 브로커가 받았다고 응답함
	SENT,
	// 메시지 자체의 문제로 포기함. 사람이 원인을 고친 뒤 PENDING으로 되돌린다
	SEND_FAILED
}
