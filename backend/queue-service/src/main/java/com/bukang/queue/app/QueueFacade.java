package com.bukang.queue.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.queue.domain.QueueRace;
import com.bukang.queue.domain.QueueRaceCourse;
import com.bukang.queue.dto.QueueAdmissionDto;
import com.bukang.queue.dto.QueueStatusDto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QueueFacade {
	private final QueueSyncRaceUseCase queueSyncRaceUseCase;
	private final QueueSyncRaceCourseUseCase queueSyncRaceCourseUseCase;
	private final QueueRegisterUseCase queueRegisterUseCase;
	private final QueueStatusUseCase queueStatusUseCase;
	private final QueuePromoteUseCase queuePromoteUseCase;
	private final QueueAdmissionUseCase queueAdmissionUseCase;

	// 트랜잭션을 걸지 않는다. 복제본 조회(대회·종목)는 각각 짧게 끝나고,
	// Redis를 기다리는 동안(장애 때는 최대 2초) DB 커넥션을 잡고 있지 않게 한다
	public QueueStatusDto register(long memberId, long raceId, long raceCourseId) {
		return queueRegisterUseCase.register(memberId, raceId, raceCourseId, LocalDateTime.now());
	}

	// 트랜잭션을 걸지 않는다. Redis만 읽으므로 기다리는 회원 모두가 폴링할 때마다 DB 커넥션을 잡을 일이 없다
	public QueueStatusDto getStatus(long memberId, long raceId, long raceCourseId) {
		return queueStatusUseCase.getStatus(memberId, raceId, raceCourseId, LocalDateTime.now());
	}

	// 주문 서비스가 내부 API로 부른다. 트랜잭션을 걸지 않는다. Redis만 읽으므로 DB 커넥션을 잡을 일이 없다
	public QueueAdmissionDto getAdmission(long memberId, long raceId, long raceCourseId) {
		return queueAdmissionUseCase.getAdmission(memberId, raceId, raceCourseId, LocalDateTime.now());
	}

	// 승격 스케줄러가 부른다. 트랜잭션을 걸지 않는다. Redis만 다루므로 DB 커넥션을 잡을 일이 없다
	public void promote() {
		queuePromoteUseCase.promote(LocalDateTime.now());
	}

	@Transactional
	public QueueRace syncRace(
		long raceId,
		RaceSaleStatus saleStatus,
		LocalDateTime individualOpenAt,
		LocalDateTime applicationCloseAt,
		LocalDateTime updatedAt
	) {
		return queueSyncRaceUseCase.syncRace(raceId, saleStatus, individualOpenAt, applicationCloseAt, updatedAt);
	}

	@Transactional
	public QueueRaceCourse syncRaceCourse(long raceCourseId, long raceId, LocalDateTime updatedAt, boolean deleted) {
		return queueSyncRaceCourseUseCase.syncRaceCourse(raceCourseId, raceId, updatedAt, deleted);
	}
}
