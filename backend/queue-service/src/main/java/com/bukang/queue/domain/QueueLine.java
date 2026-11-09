package com.bukang.queue.domain;

/**
 * 대기열 줄의 종류. 같은 대회·종목에 개인 줄과 파티 줄을 따로 둔다.
 * 개인 줄은 개인 예매 기간에 회원이, 파티 줄은 크루 예매 기간에 파티장이 선다.
 * 두 줄은 Redis 키와 승격 목록이 달라 서로 순번·입장에 영향을 주지 않는다 (키 문자열은 QueueRedisRepository가 만든다).
 */
public enum QueueLine {
	INDIVIDUAL,
	PARTY
}
