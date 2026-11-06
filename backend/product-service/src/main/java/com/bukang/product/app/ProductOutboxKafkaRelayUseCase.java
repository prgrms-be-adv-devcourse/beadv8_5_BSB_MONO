package com.bukang.product.app;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.bukang.common.shared.kafka.KafkaMessageId;
import com.bukang.product.domain.OutboxStatus;
import com.bukang.product.domain.ProductOutboxKafkaEvent;
import com.bukang.product.out.ProductOutboxKafkaEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 아웃박스의 미발행(PENDING) 메시지를 만든 순서대로 Kafka에 보낸다 (Polling Publisher).
 * 브로커가 받았다고 응답해야 SENT로 바꾼다. send()는 비동기라, 응답을 기다리지 않고 바꾸면 보내지 못한 메시지도 SENT가 된다.
 * 보내지 못하면 그 메시지의 토픽 + key(대회 ID)만 막는다. 같은 토픽·같은 대회의 뒤 메시지는 이번 실행에서 건너뛰고(순서 유지),
 * 다른 토픽이나 다른 대회의 메시지는 계속 보낸다. Kafka도 같은 토픽의 같은 key(파티션) 안에서만 순서를 지키므로,
 * 토픽이 다르면 막을 이유가 없다 (예: 이미지 연결 메시지가 막혀도 같은 대회의 대기열 메시지는 나간다).
 * 다시 보낼 때까지 간격을 1초 → 2초 → 4초 …로 늘리고(최대 1분), MAX_RETRY번 실패하면 SEND_FAILED로 바꾼다.
 * 재시도 시각이 안 된 메시지도 그 토픽·대회를 막는다. 그래서 조회는 재시도 시각으로 거르지 않고 PENDING을 모두 만든 순서대로 읽는다.
 * SEND_FAILED가 되면 그 토픽·대회의 뒤 메시지는 다음 실행부터 다시 나간다. 이때 순서가 깨지므로 SEND_FAILED는 사람이 확인한다.
 * 한 번에 BATCH_SIZE건만 읽으므로, 막힌 메시지가 그만큼 쌓이면 뒤의 다른 메시지는 막힘이 풀릴 때까지 늦어진다.
 * 브로커가 받은 뒤 SENT로 커밋하기 전에 앱이 죽으면 다음 실행에 한 번 더 보낸다 (최소 한 번 전달). 받는 쪽은 같은 메시지를 두 번 받아도 되게 처리한다.
 * 그래서 메시지마다 message-id 헤더에 아웃박스 행에 저장한 값(product-outbox-{UUID})을 붙인다. 다시 보내도 같은 값이라 받는 쪽 인박스가 거른다.
 * 앱을 여러 대 띄우면 같은 행을 함께 보낼 수 있다. 지금은 한 대만 띄우므로 행을 잠그지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductOutboxKafkaRelayUseCase {
	// 한 번에 읽을 최대 건수 (DB 부하 제한)
	static final int BATCH_SIZE = 100;
	// 이 횟수만큼 보내지 못하면 SEND_FAILED로 바꾸고 더는 보내지 않는다 (재시도 간격을 합치면 약 4분 동안 버틴다)
	static final int MAX_RETRY = 10;
	// 첫 재시도 간격. 실패할 때마다 두 배로 늘린다
	static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(1);
	// 재시도 간격의 최댓값
	static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(1);
	// 브로커 응답을 기다리는 최대 시간
	private static final long SEND_TIMEOUT_SECONDS = 5;

	private final ProductOutboxKafkaEventRepository productOutboxKafkaEventRepository;
	private final KafkaTemplate<String, String> kafkaTemplate;

	// 보낸 건수를 돌려준다. 트랜잭션은 ProductFacade가 연다 (상태 변경은 더티 체킹으로 커밋된다)
	public int relay(LocalDateTime now) {
		List<ProductOutboxKafkaEvent> events = productOutboxKafkaEventRepository
			.findByStatusOrderByCreateDateAscIdAsc(OutboxStatus.PENDING, Limit.of(BATCH_SIZE));
		// 이번 실행에서 막힌 토픽 + key. 같은 토픽·같은 key의 뒤 메시지는 보내지 않는다
		Set<String> blockedOrderKeys = new HashSet<>();
		int sent = 0;
		for (ProductOutboxKafkaEvent event : events) {
			String orderKey = orderKey(event);
			if (blockedOrderKeys.contains(orderKey)) {
				continue;
			}
			if (!event.isSendableAt(now)) {
				blockedOrderKeys.add(orderKey);
				continue;
			}
			if (!send(event)) {
				event.markSendFailed(MAX_RETRY, now.plus(retryDelay(event.getRetryCount() + 1)));
				logSendFailure(event);
				blockedOrderKeys.add(orderKey);
				continue;
			}
			event.markSent(now);
			sent++;
		}
		return sent;
	}

	// 순서를 지켜야 하는 단위: 같은 토픽의 같은 key (Kafka가 순서를 지키는 파티션 단위와 같다)
	private static String orderKey(ProductOutboxKafkaEvent event) {
		return event.getEventType() + ":" + event.getMessageKey();
	}

	// failCount번째 실패 뒤 기다릴 시간: 1초, 2초, 4초, … 최대 1분
	static Duration retryDelay(int failCount) {
		int shift = Math.min(failCount - 1, 30);
		Duration delay = FIRST_RETRY_DELAY.multipliedBy(1L << shift);
		return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
	}

	private boolean send(ProductOutboxKafkaEvent event) {
		// 메시지 ID는 아웃박스 행에 저장한 값을 그대로 보낸다 (여기서 새로 만들면 다시 보낼 때마다 값이 달라진다)
		// 메시지 key(대회 ID)는 같은 대회의 다른 메시지와 겹치므로 메시지 ID로 쓰지 않는다
		event.assignMessageIdIfAbsent();
		ProducerRecord<String, String> message = KafkaMessageId.record(event.getEventType(), event.getMessageKey(),
			event.getPayload(), event.getMessageId());
		try {
			kafkaTemplate.send(message).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			return true;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return false;
		} catch (ExecutionException | TimeoutException | RuntimeException exception) {
			// 브로커에 연결하지 못하면 send()가 기다리다(max.block.ms, 기본 60초) 예외를 던진다
			log.warn("아웃박스 메시지를 보내지 못했습니다. id={}, topic={}, key={}",
				event.getId(), event.getEventType(), event.getMessageKey(), exception);
			return false;
		}
	}

	private void logSendFailure(ProductOutboxKafkaEvent event) {
		if (event.isFailed()) {
			log.error("아웃박스 메시지를 {}번 보내지 못해 SEND_FAILED로 바꿨습니다. 원인을 고친 뒤 PENDING으로 되돌려야 합니다. "
				+ "id={}, topic={}, key={}", MAX_RETRY, event.getId(), event.getEventType(), event.getMessageKey());
		}
	}
}
