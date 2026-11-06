package com.bukang.order.in;

import java.time.LocalDateTime;

import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.KafkaMessageId;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.race.event.RaceCancelEvent;
import com.bukang.order.app.OrderInboxUseCase;
import com.bukang.order.app.RaceCancelRefundUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 다른 서비스가 보낸 Kafka 메시지를 받는다. 메시지는 JSON 문자열이고 JsonConverter로 바꾼다
 * 처리에 실패하면 재시도 토픽으로 보내 간격을 늘려 가며 다시 하고, 그래도 실패하면 <토픽>.order.dlt로 보내 로그를 남긴다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderKafkaConsumer {
	// application.yaml에 group-id가 없으므로 리스너마다 적는다
	private static final String GROUP_ID = "order";
	// 재시도·DLT 토픽 파티션 수 (기본값은 1개). 대회 취소는 드물어 1개로 충분하다
	private static final String PARTITIONS = "1";
	// 처음 시도를 포함한 시도 횟수. 예치금 서비스가 잠깐 내려가도 버티게 10초 → 30초 → 90초 간격으로 다시 한다
	private static final String RETRY_ATTEMPTS = "4";
	private static final long RETRY_DELAY_MILLIS = 10_000L;
	private static final double RETRY_MULTIPLIER = 3.0;
	// 재시도·DLT 토픽 이름에 서비스 이름을 넣어 다른 서비스의 재시도 토픽과 겹치지 않게 한다 (ProductKafkaConsumer와 같은 방식)
	private static final String RETRY_TOPIC_SUFFIX = ".order.retry";
	private static final String DLT_TOPIC_SUFFIX = ".order.dlt";

	private final RaceCancelRefundUseCase raceCancelRefundUseCase;
	private final OrderInboxUseCase orderInboxUseCase;
	private final JsonConverter jsonConverter;

	// 판매자가 대회를 취소했다 (상품 서비스가 발행, key는 대회 ID). 결제한 참가권을 모두 환불한다
	// 일부 주문의 환불이 실패하면 예외로 끝내 메시지를 다시 처리한다. 이미 환불한 주문은 건너뛰므로 실패한 주문만 다시 환불된다
	@KafkaListener(topics = KafkaTopics.ORDER_RACE_CANCEL, groupId = GROUP_ID)
	@RetryableTopic(
		attempts = RETRY_ATTEMPTS,
		backOff = @BackOff(delay = RETRY_DELAY_MILLIS, multiplier = RETRY_MULTIPLIER),
		// 깨진 JSON은 다시 해도 실패하므로 재시도 없이 바로 DLT로 보낸다 (JsonConverter가 IllegalArgumentException으로 바꾼다)
		exclude = IllegalArgumentException.class,
		numPartitions = PARTITIONS,
		retryTopicSuffix = RETRY_TOPIC_SUFFIX,
		dltTopicSuffix = DLT_TOPIC_SUFFIX
	)
	public void handleRaceCancel(
		String payload,
		@Header(name = KafkaMessageId.HEADER, required = false) String messageId
	) {
		// 같은 메시지를 다시 받으면(재전달) 대회의 주문을 다시 훑고 예치금 서비스를 다시 부르지 않게 건너뛴다 (BUK-53)
		if (orderInboxUseCase.alreadyProcessed(messageId, GROUP_ID, KafkaTopics.ORDER_RACE_CANCEL)) {
			return;
		}
		RaceCancelEvent event = jsonConverter.fromJson(payload, RaceCancelEvent.class);
		RaceCancelRefundUseCase.RefundResult result =
			raceCancelRefundUseCase.refundAll(event.raceId(), LocalDateTime.now());
		if (result.failedOrders() > 0) {
			throw new IllegalStateException("취소된 대회의 주문 %d건을 환불하지 못해 다시 처리합니다. raceId=%d"
				.formatted(result.failedOrders(), event.raceId()));
		}
		// 모두 환불한 뒤에만 남긴다. 일부가 실패해 다시 받으면 실패한 주문을 다시 환불해야 한다
		orderInboxUseCase.record(messageId, GROUP_ID, KafkaTopics.ORDER_RACE_CANCEL, LocalDateTime.now());
	}

	// 끝까지 실패한 메시지. 환불하지 못한 참가권이 남았으므로 운영자가 확인해 다시 보내야 한다
	@DltHandler
	public void handleDlt(String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
		log.error("Kafka 메시지를 끝내 처리하지 못했습니다. 확인 후 다시 보내야 합니다. topic={}, payload={}", topic, payload);
	}
}
