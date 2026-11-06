package com.bukang.queue.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.bukang.queue.domain.QueueLine;
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
		QueueRace race = findRaceOfCourse(raceId, raceCourseId);
		if (!race.isEnterable(now)) {
			throw new QueueNotEnterableException("접수 기간이 아닙니다.");
		}
		return addToLine(QueueLine.INDIVIDUAL, memberId, raceId, raceCourseId, now);
	}

	// 파티 줄 등록. 크루 예매 기간인지 판정한 뒤, 파티장 회원 ID를 그 종목의 파티 waiting에 넣는다
	// 파티장인지는 확인하지 않는다. 프론트가 등록 전에 주문 서비스의 파티장 확인 API로 막고,
	// 주문 서비스가 [대회 참가] 때 다시 확인한다
	public QueueStatusDto registerParty(long leaderMemberId, long raceId, long raceCourseId, LocalDateTime now) {
		QueueRace race = findRaceOfCourse(raceId, raceCourseId);
		if (!race.isPartyEnterable(now)) {
			throw new QueueNotEnterableException("크루 예매 기간이 아닙니다.");
		}
		return addToLine(QueueLine.PARTY, leaderMemberId, raceId, raceCourseId, now);
	}

	// 대회·종목 복제본을 읽고, 종목이 그 대회의 것인지 확인한다 (개인·파티 줄 공통)
	private QueueRace findRaceOfCourse(long raceId, long raceCourseId) {
		QueueRace race = queueRaceRepository.findById(raceId).orElseThrow(QueueRaceNotFoundException::new);
		QueueRaceCourse raceCourse = queueRaceCourseRepository.findById(raceCourseId)
			.orElseThrow(QueueRaceCourseNotFoundException::new);
		if (!raceCourse.belongsTo(raceId)) {
			throw new QueueNotEnterableException("이 대회의 종목이 아닙니다.");
		}
		return race;
	}

	// 줄 종류에 맞는 waiting에 넣는다. 이미 입장(active)했으면 다시 줄을 설 수 없다 (개인·파티 줄 공통)
	private QueueStatusDto addToLine(QueueLine line, long memberId, long raceId, long raceCourseId,
		LocalDateTime now) {
		Registration registration = queueRedisRepository.register(line, raceId, raceCourseId, memberId, now);
		if (registration.active()) {
			throw new QueueAlreadyActiveException();
		}
		return QueueStatusDto.waiting(registration.rank(), registration.total());
	}
}
