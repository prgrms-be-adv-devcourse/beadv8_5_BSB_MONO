package com.bukang.queue.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.queue.dto.QueueAdmissionDto;
import com.bukang.queue.dto.QueueStatusDto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QueueFacade {
	// 인박스에 남길 컨슈머 그룹. QueueKafkaConsumer의 groupId와 같은 값이다
	private static final String KAFKA_GROUP_ID = "queue-service";

	private final QueueSyncRaceUseCase queueSyncRaceUseCase;
	private final QueueSyncRaceCourseUseCase queueSyncRaceCourseUseCase;
	private final QueueRegisterUseCase queueRegisterUseCase;
	private final QueueStatusUseCase queueStatusUseCase;
	private final QueuePromoteUseCase queuePromoteUseCase;
	private final QueueAdmissionUseCase queueAdmissionUseCase;
	private final QueueInboxUseCase queueInboxUseCase;

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

	// Kafka로 받은 대회 변경 메시지. 인박스에 남긴 뒤 대회 복제본에 반영하고, 이미 처리한 메시지면 건너뛴다
	// 인박스 행과 복제본 저장이 한 트랜잭션이라, 저장이 실패해 롤백되면 다시 받은 메시지를 처리한다
	@Transactional
	public void syncRace(
		String messageId,
		String topic,
		long raceId,
		RaceSaleStatus saleStatus,
		LocalDateTime individualOpenAt,
		LocalDateTime applicationCloseAt,
		LocalDateTime updatedAt
	) {
		if (queueInboxUseCase.tryRecord(messageId, KAFKA_GROUP_ID, topic, LocalDateTime.now())) {
			queueSyncRaceUseCase.syncRace(raceId, saleStatus, individualOpenAt, applicationCloseAt, updatedAt);
		}
	}

	// Kafka로 받은 종목 변경 메시지. 인박스에 남긴 뒤 종목 복제본에 반영하고, 이미 처리한 메시지면 건너뛴다
	@Transactional
	public void syncRaceCourse(String messageId, String topic, long raceCourseId, long raceId,
		LocalDateTime updatedAt, boolean deleted) {
		if (queueInboxUseCase.tryRecord(messageId, KAFKA_GROUP_ID, topic, LocalDateTime.now())) {
			queueSyncRaceCourseUseCase.syncRaceCourse(raceCourseId, raceId, updatedAt, deleted);
		}
	}
}
