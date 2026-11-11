package com.bukang.queue.app;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.bukang.queue.domain.exception.QueueRaceNotFoundException;
import com.bukang.queue.dto.QueueEntryResponseDto;
import com.bukang.queue.out.QueueRaceRepository;

import lombok.RequiredArgsConstructor;

/**
 * 대회에 들어갈 대기열(파티·개인)을 오늘 날짜로 알려 준다.
 * 대기열 등록과 같은 대회 복제본(QueueRace)으로 판정해, 버튼이 가리키는 대기열과 등록 판정이 어긋나지 않게 한다.
 */
@Service
@RequiredArgsConstructor
public class QueueEntryUseCase {
	private final QueueRaceRepository queueRaceRepository;

	// 없는 대회와 취소된 대회는 404다. 취소된 대회의 대기열로 보낼 일은 없다
	public QueueEntryResponseDto findEntry(long raceId, LocalDate today) {
		return queueRaceRepository.findById(raceId)
			.filter(race -> !race.isCanceled())
			.map(race -> QueueEntryResponseDto.of(race, today))
			.orElseThrow(QueueRaceNotFoundException::new);
	}
}
