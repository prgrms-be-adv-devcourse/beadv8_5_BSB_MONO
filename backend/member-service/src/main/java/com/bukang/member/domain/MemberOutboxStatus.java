package com.bukang.member.domain;

/**
 * 아웃박스 행(MemberOutboxEvent)의 상태. 주문 서비스 OutboxStatus와 값을 맞췄다 (서비스끼리 의존하지 않아 따로 둔다)
 * 포기(SEND_FAILED)는 메시지 하나의 문제로 더는 보내지 않는 상태다. 원인을 고친 뒤 PENDING으로 되돌리면 다시 보낸다
 */
public enum MemberOutboxStatus {
	PENDING,
	SENT,
	SEND_FAILED
}
