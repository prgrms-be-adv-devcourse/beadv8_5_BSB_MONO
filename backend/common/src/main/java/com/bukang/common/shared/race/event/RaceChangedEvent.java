package com.bukang.common.shared.race.event;

import java.time.LocalDateTime;

import com.bukang.common.shared.race.domain.RaceSaleStatus;

/**
 * 대회 변경 알림. 상품 서비스가 대회를 등록·수정·취소하거나 상태 배치가 판매 상태를 바꾸면 커밋한 뒤 발행한다.
 * 대기열 서비스가 받아 대회 복제본에 덮어쓰고, 줄을 세울 수 있는지(판매 중 + 개인 예매 오픈 ≤ 지금 < 접수 마감) 판정한다.
 * 수정은 대회 칸이 바뀌었을 때만 보낸다. 같은 대회의 메시지가 순서대로 처리되도록 대회 ID를 key로 보낸다.
 */
public record RaceChangedEvent(
	long raceId,
	RaceSaleStatus saleStatus,
	LocalDateTime individualOpenAt,
	LocalDateTime applicationCloseAt,
	// 대회 행의 수정 일시(race.updated_at). 받는 쪽은 저장된 값보다 늦은 메시지만 반영해 늦게 온 옛 메시지를 건너뛴다
	LocalDateTime updatedAt
) {
}
