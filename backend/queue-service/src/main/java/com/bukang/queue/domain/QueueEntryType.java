package com.bukang.queue.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 대회에 들어갈 대기열 종류와, 그 대기열에 등록하는 API 주소.
 * 조회 응답에 주소를 함께 내려 주어, 프론트가 종류를 보고 주소를 따로 고르지 않게 한다.
 */
@Getter
@RequiredArgsConstructor
public enum QueueEntryType {
	// 파티 대기열. 등록 전에 주문 서비스의 이 대회 내 파티 상태(GET /api/v1/order/parties/me/race-status?raceId=)로
	// 파티장이고 신청할 수 있는지(role=LEADER, canApply=true) 확인한다
	PARTY("/api/v1/queue/parties"),
	// 개인 대기열
	INDIVIDUAL("/api/v1/queue");

	private final String registerUrl;
}
