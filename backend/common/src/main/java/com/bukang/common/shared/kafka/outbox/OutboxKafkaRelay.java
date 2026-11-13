package com.bukang.common.shared.kafka.outbox;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.common.shared.kafka.KafkaMessageId;

import lombok.extern.slf4j.Slf4j;

/**
 * 아웃박스의 보낼 차례 행을 저장 순서대로 Kafka에 보낸다 (BUK-200, 회원 BUK-41 발행 작업을 옮김)
 * 서비스는 이 클래스를 상속한 @Service를 만들고, 스케줄러가 1초마다 relay(now)를 부른다
 * - 행은 SELECT ... FOR UPDATE SKIP LOCKED로 읽는다. 서버 두 대가 함께 돌아도 다른 서버가 읽은 행은 건너뛰어 두 번 보내지 않는다
 * - 브로커가 받았다고 응답해야 "보냄"으로 바꾼다. 응답 전에 서버가 죽으면 다음 실행에 한 번 더 보낸다 (최소 한 번 전달)
 *   그래서 메시지마다 행에 저장한 message-id를 붙인다. 다시 보내도 같은 값이라 받는 쪽 인박스가 거른다
 * - 보내지 못하면 이번 실행을 멈춘다. 브로커 장애 중에 계속 보내면 행마다 기다려(max.block.ms) 커넥션과 락을 오래 쥔다
 * - 메시지 하나의 문제(크기 초과, 직렬화 오류, 잘못된 토픽)는 MAX_MESSAGE_FAILURES번째 실패에 포기한다.
 *   그래야 같은 대상의 뒤 이벤트가 막히지 않는다. 포기하면 순서가 바뀌므로 에러 로그를 보고 사람이 확인한다.
 *   실패 횟수에는 연결 실패도 들어 있어 더 일찍 포기할 수 있지만, 이 세 오류는 다시 보내도 같은 결과라 잃는 것이 없다
 * - 그 밖의 오류는 모두 Kafka 연결 문제로 보고 포기하지 않는다 (브로커 장애, 시간 초과, 인증 설정 오류 등).
 *   연결 장애로 포기하면 메시지를 잃으므로, 모르는 오류는 잃지 않는 쪽으로 둔다
 * - 순서 단위는 같은 대상(종류 + ID)이다. 토픽 + key로 묶으면 같은 대상의 다른 토픽 이벤트가 앞 이벤트보다 먼저 나갈 수 있다
 * - 다른 서버가 락을 건 앞 행은 SKIP LOCKED로 묶음에서 빠진다. 그래서 대상마다 가장 앞 행을 락 없이 따로 읽어,
 *   그 행이 이번 묶음에 없으면 그 대상의 뒤 행도 보내지 않는다
 */
@Slf4j
public abstract class OutboxKafkaRelay<E extends BaseOutboxKafkaEvent> {
	public static final int BATCH_SIZE = 100;
	public static final int MAX_MESSAGE_FAILURES = 3;
	public static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(1);
	public static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(1);
	// 이보다 오래 남은 보낼 차례 행이 있으면 에러 로그를 남긴다. 장애 중 매초 남지 않게 1분에 한 번만 남긴다
	private static final Duration STALE_PENDING = Duration.ofMinutes(5);
	private static final Duration STALE_LOG_INTERVAL = Duration.ofMinutes(1);
	private static final long SEND_TIMEOUT_SECONDS = 5;

	private final OutboxKafkaEventRepository<E> repository;
	private final KafkaTemplate<String, String> kafkaTemplate;
	// 발행 작업은 스케줄러 스레드 하나에서만 돈다
	private LocalDateTime lastStaleLogAt;

	protected OutboxKafkaRelay(OutboxKafkaEventRepository<E> repository, KafkaTemplate<String, String> kafkaTemplate) {
		this.repository = repository;
		this.kafkaTemplate = kafkaTemplate;
	}

	// 보낸 건수를 돌려준다. 상태 변경은 더티 체킹으로 커밋된다
	// READ COMMITTED: MySQL 기본(REPEATABLE READ)이면 FOR UPDATE가 인덱스 끝 빈 구간에도 락을 걸어,
	// 보내는 동안 새 아웃박스 행 저장이 함께 기다린다
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public int relay(LocalDateTime now) {
		List<E> events = repository.findByStatusOrderByIdAsc(OutboxStatus.PENDING, Limit.of(BATCH_SIZE));
		if (events.isEmpty()) {
			return 0;
		}
		logIfStale(events.get(0), now);
		Set<Long> firstIds = new HashSet<>(
			repository.findFirstIdsPerTarget(OutboxStatus.PENDING, events.get(events.size() - 1).getId()));
		// 재시도 시각이 안 됐거나 앞 행이 다른 서버에 있는 대상. 같은 대상의 뒤 행은 보내지 않는다
		Set<String> blockedTargets = new HashSet<>();
		Set<String> seenTargets = new HashSet<>();
		int sent = 0;
		for (E event : events) {
			String target = event.getRefType() + ":" + event.getRefId();
			if (blockedTargets.contains(target)) {
				continue;
			}
			boolean firstInBatch = seenTargets.add(target);
			if ((firstInBatch && !firstIds.contains(event.getId())) || !event.isSendableAt(now)) {
				blockedTargets.add(target);
				continue;
			}
			Exception failure = send(event);
			if (failure != null) {
				markFailed(event, failure, now);
				break;
			}
			event.markSent(now);
			sent++;
		}
		return sent;
	}

	// failCount번째 실패 뒤 기다릴 시간: 1초, 2초, 4초, … 최대 1분
	public static Duration retryDelay(int failCount) {
		int shift = Math.min(failCount - 1, 30);
		Duration delay = FIRST_RETRY_DELAY.multipliedBy(1L << shift);
		return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
	}

	private void logIfStale(E oldest, LocalDateTime now) {
		if (!oldest.getCreateDate().isBefore(now.minus(STALE_PENDING))) {
			return;
		}
		// 테스트처럼 시각이 거꾸로 가도 남도록 차이의 절댓값으로 본다
		if (lastStaleLogAt != null && Duration.between(lastStaleLogAt, now).abs().compareTo(STALE_LOG_INTERVAL) < 0) {
			return;
		}
		lastStaleLogAt = now;
		log.error("보낼 차례 아웃박스 행이 5분 넘게 남아 있습니다. id={}, topic={}, createDate={}, headers={}",
			oldest.getId(), oldest.getTopic(), oldest.getCreateDate(), oldest.extraHeaders());
	}

	// 보내지 못했으면 원인을 돌려준다
	private Exception send(E event) {
		try {
			kafkaTemplate.send(record(event)).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			return null;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return exception;
		} catch (ExecutionException | TimeoutException | RuntimeException exception) {
			return exception;
		}
	}

	private ProducerRecord<String, String> record(E event) {
		// 메시지 ID는 행에 저장한 값을 그대로 보낸다 (여기서 새로 만들면 다시 보낼 때마다 값이 달라진다)
		event.assignMessageIdIfAbsent();
		ProducerRecord<String, String> record = KafkaMessageId.record(event.getTopic(),
			String.valueOf(event.getRefId()), event.getPayload(), event.getMessageId());
		event.extraHeaders().forEach((name, value) ->
			record.headers().add(name, value.getBytes(StandardCharsets.UTF_8)));
		return record;
	}

	private void markFailed(E event, Exception failure, LocalDateTime now) {
		boolean messageProblem = isMessageProblem(failure);
		boolean giveUp = messageProblem && event.getRetryCount() + 1 >= MAX_MESSAGE_FAILURES;
		event.markSendFailed(giveUp, now.plus(retryDelay(event.getRetryCount() + 1)));
		if (giveUp) {
			log.error("아웃박스 메시지를 {}번 보내지 못해 포기(SEND_FAILED)로 바꿨습니다. 원인을 고친 뒤 PENDING으로 되돌려야 합니다. "
				+ "id={}, topic={}, key={}, headers={}", MAX_MESSAGE_FAILURES, event.getId(), event.getTopic(),
				event.getRefId(), event.extraHeaders(), failure);
			return;
		}
		// 장애 중에는 1초마다 남으므로 스택 없이 한 줄로 남긴다
		log.warn("아웃박스 메시지를 보내지 못해 이번 실행을 멈춥니다. id={}, topic={}, key={}, 메시지 문제={}, cause={}",
			event.getId(), event.getTopic(), event.getRefId(), messageProblem, failure.toString());
	}

	// 원인 사슬에 다시 보내도 같은 결과인 오류가 있으면 메시지 하나의 문제로 본다
	private static boolean isMessageProblem(Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof RecordTooLargeException || cause instanceof SerializationException
				|| cause instanceof InvalidTopicException) {
				return true;
			}
		}
		return false;
	}
}
