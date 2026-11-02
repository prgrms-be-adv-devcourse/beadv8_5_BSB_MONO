package com.bukang.common.shared.race.event;

import java.time.LocalDateTime;

/**
 * 대회 취소 알림. 판매자가 대회를 취소하면 상품 서비스가 커밋한 뒤 발행한다.
 * 주문 서비스가 받아 이 대회의 참가권을 일괄 환불한다 (키트 포함 전액, 피그잼 정책 1차 5장 4번).
 * 판매 시작 일시 전이라 산 사람이 없는 대회도 보낸다. 대회 ID를 key로 보낸다.
 */
public record RaceCancelEvent(
	long raceId,
	long sellerId,
	LocalDateTime canceledAt
) {
}
