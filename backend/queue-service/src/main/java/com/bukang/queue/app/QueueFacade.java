package com.bukang.queue.app;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.bukang.common.shared.kafka.dlt.DltKafkaEventDto;
import com.bukang.common.shared.kafka.dlt.DltKafkaEventStatus;
import com.bukang.common.shared.kafka.dlt.DltKafkaResendException;
import com.bukang.common.shared.kafka.dlt.KafkaDltMessage;
import com.bukang.common.shared.race.domain.RaceSaleStatus;
import com.bukang.queue.domain.QueueDltKafkaEvent;
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
	private final TransactionTemplate transactionTemplate;
	private final QueueInboxUseCase queueInboxUseCase;
	private final QueueDltUseCase queueDltUseCase;

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

	// 파티 줄 등록. 트랜잭션을 걸지 않는 이유는 개인 등록(register)과 같다
	public QueueStatusDto registerParty(long leaderMemberId, long raceId, long raceCourseId) {
		return queueRegisterUseCase.registerParty(leaderMemberId, raceId, raceCourseId, LocalDateTime.now());
	}

	// 파티 줄 순번 조회. Redis만 읽어 트랜잭션을 걸지 않는다
	public QueueStatusDto getPartyStatus(long leaderMemberId, long raceId, long raceCourseId) {
		return queueStatusUseCase.getPartyStatus(leaderMemberId, raceId, raceCourseId, LocalDateTime.now());
	}

	// 주문 서비스가 파티 [대회 참가] 때 내부 API로 부른다. Redis만 읽어 트랜잭션을 걸지 않는다
	public QueueAdmissionDto getPartyAdmission(long leaderMemberId, long raceId, long raceCourseId) {
		return queueAdmissionUseCase.getPartyAdmission(leaderMemberId, raceId, raceCourseId, LocalDateTime.now());
	}

	// 승격 스케줄러가 부른다. 트랜잭션을 걸지 않는다. Redis만 다루므로 DB 커넥션을 잡을 일이 없다
	public void promote() {
		queuePromoteUseCase.promote(LocalDateTime.now());
	}

	// Kafka로 받은 대회 변경 메시지. 인박스에 남긴 뒤 대회 복제본에 반영하고, 이미 처리한 메시지면 건너뛴다
	// 인박스 행과 복제본 저장이 한 트랜잭션이라, 저장이 실패해 롤백되면 다시 받은 메시지를 처리한다
	// DLT에서 다시 보낸 메시지였으면 같은 트랜잭션에서 실패 행을 처리 완료로 바꾼다 (Kafka 메시지 2개 메서드 모두 같다)
	@Transactional
	public void syncRace(
		String messageId,
		String topic,
		long raceId,
		RaceSaleStatus saleStatus,
		LocalDateTime individualOpenAt,
		LocalDateTime applicationCloseAt,
		LocalDateTime crewSaleStartAt,
		LocalDateTime crewSaleEndAt,
		LocalDateTime updatedAt
	) {
		LocalDateTime now = LocalDateTime.now();
		if (queueInboxUseCase.tryRecord(messageId, KAFKA_GROUP_ID, topic, now)) {
			queueSyncRaceUseCase.syncRace(raceId, saleStatus, individualOpenAt, applicationCloseAt,
				crewSaleStartAt, crewSaleEndAt, updatedAt);
		}
		queueDltUseCase.completeByResend(messageId, now);
	}

	// Kafka로 받은 종목 변경 메시지. 인박스에 남긴 뒤 종목 복제본에 반영하고, 이미 처리한 메시지면 건너뛴다
	@Transactional
	public void syncRaceCourse(String messageId, String topic, long raceCourseId, long raceId,
		LocalDateTime updatedAt, boolean deleted) {
		LocalDateTime now = LocalDateTime.now();
		if (queueInboxUseCase.tryRecord(messageId, KAFKA_GROUP_ID, topic, now)) {
			queueSyncRaceCourseUseCase.syncRaceCourse(raceCourseId, raceId, updatedAt, deleted);
		}
		queueDltUseCase.completeByResend(messageId, now);
	}

	// 재시도를 모두 실패해 DLT로 온 메시지를 실패 테이블에 남기고, 커밋 뒤 Slack으로 알린다 (QueueKafkaDltConsumer가 부른다)
	// 같은 메시지는 행 하나로 상태만 바꾼다. 저장이 롤백되면 알림도 보내지 않는다
	@Transactional
	public void recordDlt(KafkaDltMessage message) {
		queueDltUseCase.record(message);
	}

	// DLT 실패 행 목록 (내부 API). 최근 것부터 size건
	// 이 클래스에는 클래스 단위 읽기 전용 트랜잭션이 없으므로 메서드에 붙인다
	@Transactional(readOnly = true)
	public List<DltKafkaEventDto> findDlts(DltKafkaEventStatus status, int size) {
		return queueDltUseCase.findByStatus(status, size).stream().map(DltKafkaEventDto::from).toList();
	}

	// 사람이 원인을 고친 뒤 DLT 메시지를 첫 재시도 토픽으로 다시 보낸다 (내부 API)
	// ① 처리 실패 → 다시 보냄을 먼저 커밋한다. 보낸 메시지를 본 리스너가 곧바로 처리해도 이 행을 처리 완료로 바꿀 수 있게 하기 위해서다
	// ② 트랜잭션 밖에서 Kafka로 보낸다
	// ③ 보내지 못하면 처리 실패로 되돌려 커밋하고 예외(503)를 그대로 던진다. 브로커가 돌아오면 다시 부르면 된다
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public DltKafkaEventDto resendDlt(long id) {
		QueueDltKafkaEvent event = transactionTemplate.execute(status ->
			queueDltUseCase.markResent(id, LocalDateTime.now()));
		try {
			queueDltUseCase.send(event);
		} catch (DltKafkaResendException exception) {
			transactionTemplate.executeWithoutResult(status -> queueDltUseCase.cancelResent(id));
			throw exception;
		}
		return DltKafkaEventDto.from(event);
	}

	// 사람이 원인을 고친 뒤 메모와 함께 처리 완료로 닫는다 (내부 API)
	@Transactional
	public DltKafkaEventDto completeDlt(long id, String memo) {
		return DltKafkaEventDto.from(queueDltUseCase.completeManually(id, memo, LocalDateTime.now()));
	}
}
