package com.bukang.order.app;

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

import com.bukang.common.shared.kafka.KafkaMessageId;
import com.bukang.order.domain.OrderOutboxKafkaEvent;
import com.bukang.order.domain.OutboxStatus;
import com.bukang.order.out.OrderOutboxKafkaEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 아웃박스의 미발행(PENDING) 메시지를 만든 순서대로 Kafka에 보낸다 (Polling Publisher, 상품 ProductOutboxKafkaRelayUseCase와 같은 방식)
 * 브로커가 받았다고 응답해야 SENT로 바꾼다. send()는 비동기라, 응답을 기다리지 않고 바꾸면 보내지 못한 메시지도 SENT가 된다.
 * 보내지 못하면 이번 실행을 거기서 끝낸다. 자리 메시지는 본문이 참가 신청 ID뿐이라 실패는 거의 브로커 장애이고, 그때는 모든 메시지가
 * 실패한다. 계속 보내면 행마다 브로커를 기다려(max.block.ms 3초) 트랜잭션과 DB 커넥션을 몇 분씩 쥐게 된다 (상품 아웃박스와 다른 점)
 * 실패한 메시지는 재시도 시각까지 그 토픽 + key(참가 신청 ID)만 막는다. 다음 실행에서 같은 토픽·같은 신청의 뒤 메시지는 건너뛰고(순서 유지),
 * 다른 토픽이나 다른 신청의 메시지는 보낸다. Kafka도 같은 토픽의 같은 key(파티션) 안에서만 순서를 지키므로 토픽이 다르면 막지 않는다.
 * 다시 보낼 때까지 간격을 1초 → 2초 → 4초 …로 늘리고(최대 1분), MAX_RETRY번 실패하면 SEND_FAILED로 바꾼다.
 * 재시도 시각이 안 된 메시지도 그 토픽·신청을 막는다. 그래서 조회는 재시도 시각으로 거르지 않고 PENDING을 모두 만든 순서대로 읽는다.
 * SEND_FAILED가 되면 자리가 묶이거나(해제) 정원으로 돌아오지 않는다(복구). 정원보다 덜 팔릴 뿐 더 팔리지는 않지만, 사람이 확인해 되돌린다.
 * 한 번에 BATCH_SIZE건만 읽으므로, 막힌 메시지가 그만큼 쌓이면 뒤의 다른 메시지는 막힘이 풀릴 때까지 늦어진다.
 * 브로커가 받은 뒤 SENT로 커밋하기 전에 앱이 죽으면 다음 실행에 한 번 더 보낸다 (최소 한 번 전달). 상품의 복구·해제는 멱등이라 괜찮다.
 * 앱을 여러 대 띄우면 같은 행을 함께 보낼 수 있다. 지금은 한 대만 띄우므로 행을 잠그지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderOutboxKafkaRelayUseCase {
	// 한 번에 읽을 최대 건수 (DB 부하 제한)
	static final int BATCH_SIZE = 100;
	// 이 횟수만큼 보내지 못하면 SEND_FAILED로 바꾸고 더는 보내지 않는다. 재시도 간격을 합치면 약 1시간 동안 버틴다
	// (1초~32초 6번 + 1분 60번). 상품 아웃박스(10번, 약 4분)보다 길게 둔다: 자리 메시지는 본문이 참가 신청 ID뿐이라
	// 보내지 못하는 까닭이 거의 브로커 장애이고, 전에는 만료가 Kafka가 돌아올 때까지 매분 다시 보냈기 때문이다
	static final int MAX_RETRY = 66;
	// 첫 재시도 간격. 실패할 때마다 두 배로 늘린다
	static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(1);
	// 재시도 간격의 최댓값
	static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(1);
	// 브로커 응답을 기다리는 최대 시간
	private static final long SEND_TIMEOUT_SECONDS = 5;

	private final OrderOutboxKafkaEventRepository orderOutboxKafkaEventRepository;
	private final KafkaTemplate<String, String> kafkaTemplate;

	// 보낸 건수를 돌려준다. 상태 변경은 더티 체킹으로 커밋된다
	@Transactional
	public int relay(LocalDateTime now) {
		List<OrderOutboxKafkaEvent> events = orderOutboxKafkaEventRepository
			.findByStatusOrderByCreateDateAscIdAsc(OutboxStatus.PENDING, Limit.of(BATCH_SIZE));
		// 재시도 시각이 안 된 메시지의 토픽 + key. 같은 토픽·같은 key의 뒤 메시지는 보내지 않는다
		Set<String> blockedKeys = new HashSet<>();
		int sent = 0;
		for (OrderOutboxKafkaEvent event : events) {
			String sequenceKey = sequenceKey(event);
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

	// 순서를 지켜야 하는 단위: 같은 토픽의 같은 key (Kafka가 순서를 지키는 파티션 단위와 같다)
	private static String sequenceKey(OrderOutboxKafkaEvent event) {
		return event.getEventType() + ":" + event.getMessageKey();
	}

	// failCount번째 실패 뒤 기다릴 시간: 1초, 2초, 4초, … 최대 1분
	static Duration retryDelay(int failCount) {
		int shift = Math.min(failCount - 1, 30);
		Duration delay = FIRST_RETRY_DELAY.multipliedBy(1L << shift);
		return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
	}

	private boolean send(OrderOutboxKafkaEvent event) {
		try {
			// 받는 서비스가 같은 메시지를 두 번 처리하지 않게 행에 저장한 메시지 ID를 헤더로 붙인다 (#285 인박스, BUK-81)
			// 여기서 새로 만들면 다시 보낼 때마다 값이 달라지므로 저장한 값을 그대로 보낸다
			kafkaTemplate.send(KafkaMessageId.record(event.getEventType(), event.getMessageKey(), event.getPayload(),
					event.getMessageId()))
				.get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			return true;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return false;
		} catch (ExecutionException | TimeoutException | RuntimeException exception) {
			// 브로커에 연결하지 못하면 send()가 기다리다(max.block.ms, application.yaml에서 3초) 예외를 던진다
			// 장애 중에는 1초마다 남으므로 스택 없이 한 줄로 남긴다
			log.warn("아웃박스 메시지를 보내지 못해 이번 릴레이를 멈춥니다. id={}, topic={}, key={}, cause={}",
				event.getId(), event.getEventType(), event.getMessageKey(), exception.toString());
			return false;
		}
	}

	private void logSendFailure(OrderOutboxKafkaEvent event) {
		if (event.isFailed()) {
			log.error("아웃박스 메시지를 {}번 보내지 못해 SEND_FAILED로 바꿨습니다. 원인을 고친 뒤 PENDING으로 되돌려야 합니다. "
				+ "id={}, topic={}, key={}", MAX_RETRY, event.getId(), event.getEventType(), event.getMessageKey());
		}
	}
}
