package com.bukang.common.shared.race.event;

import java.time.LocalDateTime;

/**
 * 종목 변경 알림. 상품 서비스가 대회를 등록·수정하면 커밋한 뒤 종목마다 발행한다.
 * 대기열 서비스가 받아 종목 복제본에 저장하고, 줄을 세울 때 그 대회에 있는 종목인지 확인한다.
 * 등록은 종목 전부, 수정은 바뀐 종목·새 종목·뺀 종목만 보낸다. 취소·상태 배치는 대회 메시지(RaceChangedEvent)만 보낸다.
 * 같은 대회의 종목 메시지가 순서대로 처리되도록 대회 ID를 key로 보낸다.
 */
public record RaceCourseChangedEvent(
	long raceCourseId,
	long raceId,
	// 종목 행의 수정 일시(race_course.updated_at). 받는 쪽은 저장된 값보다 늦은 메시지만 반영해 늦게 온 옛 메시지를 건너뛴다
	// 뺀 종목은 지운 행에 남은 값이 없어 삭제 시각을 담는다
	LocalDateTime updatedAt,
	// 대회에서 뺀 종목이면 true. 받는 쪽은 복제본 행을 지우지 않고 삭제 표시만 남긴다
	boolean deleted
) {
}
