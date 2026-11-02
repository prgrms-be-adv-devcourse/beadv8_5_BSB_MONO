package com.bukang.queue.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.bukang.queue.domain.QueueRace;
import com.bukang.queue.domain.QueueRaceCourse;
import com.bukang.queue.domain.exception.QueueAlreadyActiveException;
import com.bukang.queue.domain.exception.QueueNotEnterableException;
import com.bukang.queue.domain.exception.QueueRaceCourseNotFoundException;
import com.bukang.queue.domain.exception.QueueRaceNotFoundException;
import com.bukang.queue.dto.QueueStatusDto;
import com.bukang.queue.out.QueueRaceCourseRepository;
import com.bukang.queue.out.QueueRaceRepository;
import com.bukang.queue.out.QueueRedisRepository;
import com.bukang.queue.out.QueueRedisRepository.Registration;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QueueRegisterUseCase {
	private final QueueRaceRepository queueRaceRepository;
	private final QueueRaceCourseRepository queueRaceCourseRepository;
	private final QueueRedisRepository queueRedisRepository;

	// 대회·종목 복제본으로 지금 줄을 설 수 있는지 판정한 뒤, 회원을 그 종목의 waiting에 넣는다
	// 같은 회원은 여러 기기에서 등록해도 한 자리다 (이미 줄에 있으면 처음 순번을 유지한다)
	public QueueStatusDto register(long memberId, long raceId, long raceCourseId, LocalDateTime now) {
		QueueRace race = queueRaceRepository.findById(raceId).orElseThrow(QueueRaceNotFoundException::new);
		QueueRaceCourse raceCourse = queueRaceCourseRepository.findById(raceCourseId)
			.orElseThrow(QueueRaceCourseNotFoundException::new);
		if (!raceCourse.belongsTo(raceId)) {
			throw new QueueNotEnterableException("이 대회의 종목이 아닙니다.");
		}
		if (!race.isEnterable(now)) {
			throw new QueueNotEnterableException("접수 기간이 아닙니다.");
		}

		Registration registration = queueRedisRepository.register(raceId, raceCourseId, memberId, now);
		if (registration.active()) {
			throw new QueueAlreadyActiveException();
		}
		return QueueStatusDto.waiting(registration.rank(), registration.total());
	}
}
