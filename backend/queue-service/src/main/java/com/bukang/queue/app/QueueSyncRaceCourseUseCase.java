package com.bukang.queue.app;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.bukang.queue.domain.QueueRaceCourse;
import com.bukang.queue.out.QueueRaceCourseRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueSyncRaceCourseUseCase {
	private final QueueRaceCourseRepository queueRaceCourseRepository;

	// 원본 값으로 덮어쓴다 (ID가 있으면 UPDATE, 없으면 INSERT). 뺀 종목(deleted=true)도 행을 지우지 않고 삭제 표시로 덮어쓴다
	// 저장된 수정 일시보다 늦은 메시지만 반영한다. 재시도 토픽을 거쳐 늦게 온 옛 메시지가 뺀 종목을 되살리지 않고,
	// 같은 메시지가 다시 오면 수정 일시가 같아 건너뛰므로 결과가 같다
	// 복제본에 없는 종목의 삭제 메시지도 삭제 표시 행으로 저장한다. 그 뒤에 등록 메시지가 늦게 오면 수정 일시가 더 일러 건너뛴다
	// 행을 잠그고 읽어, 같은 종목의 메시지가 동시에 와도 최신 커밋 값과 비교한다 (QueueRaceCourseRepository.findByIdForUpdate)
	public QueueRaceCourse syncRaceCourse(long raceCourseId, long raceId, LocalDateTime updatedAt, boolean deleted) {
		Optional<QueueRaceCourse> stored = queueRaceCourseRepository.findByIdForUpdate(raceCourseId);
		if (stored.isPresent() && !updatedAt.isAfter(stored.get().getUpdatedAt())) {
			log.info("저장된 값보다 늦지 않은 종목 메시지를 건너뜁니다. raceCourseId={}, updatedAt={}, storedUpdatedAt={}",
				raceCourseId, updatedAt, stored.get().getUpdatedAt());
			return stored.get();
		}

		return queueRaceCourseRepository.save(new QueueRaceCourse(raceCourseId, raceId, updatedAt, deleted));
	}
}
