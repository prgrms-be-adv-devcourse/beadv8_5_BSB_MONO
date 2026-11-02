package com.bukang.queue.app;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.queue.domain.QueueRace;
import com.bukang.queue.out.QueueRaceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueSyncRaceUseCase {
	private final QueueRaceRepository queueRaceRepository;

	// 원본 값으로 덮어쓴다 (ID가 있으면 UPDATE, 없으면 INSERT)
	// 저장된 수정 일시보다 늦은 메시지만 반영한다. 재시도 토픽을 거쳐 늦게 온 옛 메시지가 새 판매 상태·일정을 되돌리지 않고,
	// 같은 메시지가 다시 오면 수정 일시가 같아 건너뛰므로 결과가 같다
	// 행을 잠그고 읽어, 같은 대회의 메시지가 동시에 와도 최신 커밋 값과 비교한다 (QueueRaceRepository.findByIdForUpdate)
	public QueueRace syncRace(
		long raceId,
		RaceSaleStatus saleStatus,
		LocalDateTime individualOpenAt,
		LocalDateTime applicationCloseAt,
		LocalDateTime updatedAt
	) {
		Optional<QueueRace> stored = queueRaceRepository.findByIdForUpdate(raceId);
		if (stored.isPresent() && !updatedAt.isAfter(stored.get().getUpdatedAt())) {
			log.info("저장된 값보다 늦지 않은 대회 메시지를 건너뜁니다. raceId={}, updatedAt={}, storedUpdatedAt={}",
				raceId, updatedAt, stored.get().getUpdatedAt());
			return stored.get();
		}

		return queueRaceRepository.save(
			new QueueRace(raceId, saleStatus, individualOpenAt, applicationCloseAt, updatedAt));
	}
}
