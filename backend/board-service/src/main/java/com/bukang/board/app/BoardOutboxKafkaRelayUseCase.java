package com.bukang.board.app;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.board.domain.BoardOutboxKafkaEvent;
import com.bukang.board.domain.OutboxStatus;
import com.bukang.board.out.BoardOutboxKafkaEventRepository;
import com.bukang.common.shared.kafka.KafkaMessageId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 아웃박스의 미발행(PENDING) 메시지를 만든 순서대로 Kafka에 보낸다 (Polling Publisher, 주문 OrderOutboxKafkaRelayUseCase와 같은 방식)
 * 브로커가 받았다고 응답해야 SENT로 바꾼다. 응답을 기다리지 않고 바꾸면 보내지 못한 메시지도 SENT가 된다.
 * 보내지 못하면 이번 실행을 거기서 끝낸다. 브로커가 멈춘 것이라 계속 보내면 행마다 기다리며(max.block.ms 3초) DB 커넥션을 쥔다.
 * 실패한 메시지는 재시도 시각까지 그 토픽 + key(글 ID)만 막는다. 간격은 1초 → 2초 → 4초 …(최대 1분)로 늘리고,
 * MAX_RETRY번 실패하면 SEND_FAILED로 바꾼다. 재시도 간격을 합치면 약 1시간 동안 버틴다 (Kafka가 살아나면 밀린 글이 나간다).
 * SEND_FAILED가 되면 그 글의 색인이 어긋나므로 사람이 PENDING으로 되돌린다 (게시판이 뜰 때 도는 색인 채우기도 바로잡는다).
 * 브로커가 받은 뒤 SENT로 커밋하기 전에 앱이 죽으면 다음 실행에 한 번 더 보낸다 (최소 한 번 전달).
 * 메시지마다 행에 저장한 message-id(board-outbox-{UUID})를 붙여, 받는 쪽 인박스가 같은 메시지를 거른다.
 * 앱을 여러 대 띄우면 같은 행을 함께 보낼 수 있다. 지금은 한 대만 띄우므로 행을 잠그지 않는다 (받는 쪽이 같은 메시지를 거른다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoardOutboxKafkaRelayUseCase {
	// 한 번에 읽을 최대 건수 (DB 부하 제한)
	static final int BATCH_SIZE = 100;
	// 이 횟수만큼 보내지 못하면 SEND_FAILED로 바꾸고 더는 보내지 않는다 (1초~32초 6번 + 1분 60번, 약 1시간)
	static final int MAX_RETRY = 66;
	static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(1);
	static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(1);
	// 브로커 응답을 기다리는 최대 시간
	private static final long SEND_TIMEOUT_SECONDS = 5;

	private final BoardOutboxKafkaEventRepository boardOutboxKafkaEventRepository;
	private final KafkaTemplate<String, String> kafkaTemplate;

	// 보낸 건수를 돌려준다. 상태 변경은 더티 체킹으로 커밋된다
	@Transactional
	public int relay(LocalDateTime now) {
		List<BoardOutboxKafkaEvent> events = boardOutboxKafkaEventRepository
			.findByStatusOrderByCreateDateAscIdAsc(OutboxStatus.PENDING, Limit.of(BATCH_SIZE));
		// 재시도 시각이 안 된 메시지의 토픽 + key. 같은 토픽·같은 key의 뒤 메시지는 보내지 않는다
		Set<String> blockedKeys = new HashSet<>();
		int sent = 0;
		for (BoardOutboxKafkaEvent event : events) {
			String sequenceKey = event.getEventType() + ":" + event.getMessageKey();
			if (blockedKeys.contains(sequenceKey)) {
				continue;
			}
			if (!event.isSendableAt(now)) {
				blockedKeys.add(sequenceKey);
				continue;
			}
			if (!send(event)) {
				event.markSendFailed(MAX_RETRY, now.plus(retryDelay(event.getRetryCount() + 1)));
				logSendFailure(event);
				break;
			}
			event.markSent(now);
			sent++;
		}
		return sent;
	}

	// failCount번째 실패 뒤 기다릴 시간: 1초, 2초, 4초, … 최대 1분
	static Duration retryDelay(int failCount) {
		int shift = Math.min(failCount - 1, 30);
		Duration delay = FIRST_RETRY_DELAY.multipliedBy(1L << shift);
		return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
	}

	private boolean send(BoardOutboxKafkaEvent event) {
		try {
			kafkaTemplate.send(KafkaMessageId.record(event.getEventType(), event.getMessageKey(), event.getPayload(),
				event.getMessageId())).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			return true;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return false;
		} catch (ExecutionException | TimeoutException | RuntimeException exception) {
			// 장애 중에는 1초마다 남으므로 스택 없이 한 줄로 남긴다
			log.warn("아웃박스 메시지를 보내지 못해 이번 릴레이를 멈춥니다. id={}, topic={}, key={}, cause={}",
				event.getId(), event.getEventType(), event.getMessageKey(), exception.toString());
			return false;
		}
	}

	private void logSendFailure(BoardOutboxKafkaEvent event) {
		if (event.isFailed()) {
			log.error("아웃박스 메시지를 {}번 보내지 못해 SEND_FAILED로 바꿨습니다. 원인을 고친 뒤 PENDING으로 되돌려야 합니다. "
				+ "id={}, topic={}, key={}", MAX_RETRY, event.getId(), event.getEventType(), event.getMessageKey());
		}
	}
}
