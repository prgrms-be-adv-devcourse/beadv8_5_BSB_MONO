package com.bukang.product.app;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.bukang.product.domain.OutboxStatus;
import com.bukang.product.domain.ProductOutboxEvent;
import com.bukang.product.out.ProductOutboxEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 아웃박스의 미발행(PENDING) 메시지를 만든 순서대로 Kafka에 보낸다 (Polling Publisher).
 * 브로커가 받았다고 응답해야 SENT로 바꾼다. send()는 비동기라, 응답을 기다리지 않고 바꾸면 보내지 못한 메시지도 SENT가 된다.
 * 보내지 못하면 재시도 횟수를 올리고 이번 실행을 멈춘다. 뒤의 메시지를 먼저 보내면 같은 대회의 메시지 순서가 바뀌기 때문이다.
 * 브로커가 받은 뒤 SENT로 커밋하기 전에 앱이 죽으면 다음 실행에 한 번 더 보낸다 (최소 한 번 전달). 받는 쪽은 같은 메시지를 두 번 받아도 되게 처리한다.
 * 앱을 여러 대 띄우면 같은 행을 함께 보낼 수 있다. 지금은 한 대만 띄우므로 행을 잠그지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductOutboxRelayUseCase {
	// 한 번에 보낼 최대 건수 (DB 부하 제한)
	static final int BATCH_SIZE = 100;
	// 이 횟수만큼 보내지 못하면 FAILED로 바꾸고 더는 보내지 않는다
	static final int MAX_RETRY = 5;
	// 브로커 응답을 기다리는 최대 시간
	private static final long SEND_TIMEOUT_SECONDS = 5;

	private final ProductOutboxEventRepository productOutboxEventRepository;
	private final KafkaTemplate<String, String> kafkaTemplate;

	// 보낸 건수를 돌려준다. 트랜잭션은 ProductFacade가 연다 (상태 변경은 더티 체킹으로 커밋된다)
	public int relay(LocalDateTime now) {
		List<ProductOutboxEvent> events = productOutboxEventRepository
			.findByStatusOrderByCreateDateAscIdAsc(OutboxStatus.PENDING, Limit.of(BATCH_SIZE));
		int sent = 0;
		for (ProductOutboxEvent event : events) {
			if (!send(event)) {
				event.markSendFailed(MAX_RETRY);
				logSendFailure(event);
				break;
			}
			event.markSent(now);
			sent++;
		}
		return sent;
	}

	private boolean send(ProductOutboxEvent event) {
		try {
			kafkaTemplate.send(event.getEventType(), event.getMessageKey(), event.getPayload())
				.get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
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

	private void logSendFailure(ProductOutboxEvent event) {
		if (event.isFailed()) {
			log.error("아웃박스 메시지를 {}번 보내지 못해 FAILED로 바꿨습니다. 원인을 고친 뒤 PENDING으로 되돌려야 합니다. "
				+ "id={}, topic={}, key={}", MAX_RETRY, event.getId(), event.getEventType(), event.getMessageKey());
		}
	}
}
