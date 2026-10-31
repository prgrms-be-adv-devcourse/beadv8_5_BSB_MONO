package com.bukang.payout.in;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.order.event.OrderItemRefundedEvent;
import com.bukang.common.shared.race.event.RaceCancelEvent;
import com.bukang.payout.app.PayoutRaceCancelUseCase;
import com.bukang.payout.app.PayoutRefundUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 다른 서비스가 보낸 Kafka 메시지를 받는다. 메시지는 JSON 문자열이고 JsonConverter로 바꾼다
 * 처리에 실패하면 재시도 토픽으로 보내 간격을 늘려 가며 다시 하고, 그래도 실패하면 <토픽>.payout.dlt로 보내 로그를 남긴다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutKafkaConsumer {
	// 주문 서비스(group "order")와 groupId가 달라, 같은 대회 취소 메시지를 정산도 따로 받는다
	private static final String GROUP_ID = "payout";
	// 재시도·DLT 토픽 파티션 수. 대회 취소는 드물어 1개로 충분하다
	private static final String PARTITIONS = "1";
	// 처음 시도를 포함한 시도 횟수. 지급 배치와 같은 정산 내역을 동시에 바꿔 낙관적 락이 실패해도 다음 시도에서 걸린다
	private static final String RETRY_ATTEMPTS = "4";
	private static final long RETRY_DELAY_MILLIS = 10_000L;
	private static final double RETRY_MULTIPLIER = 3.0;
	// 재시도·DLT 토픽 이름에 서비스 이름을 넣어 주문 서비스의 재시도 토픽(.order.retry)과 겹치지 않게 한다
	private static final String RETRY_TOPIC_SUFFIX = ".payout.retry";
	private static final String DLT_TOPIC_SUFFIX = ".payout.dlt";

	// 서버 시간대와 상관없이 한국 시간으로 상태 이력 · 회수 요청 시각을 남긴다 (다른 정산 진입점과 같은 기준)
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	private final PayoutRaceCancelUseCase payoutRaceCancelUseCase;
	private final PayoutRefundUseCase payoutRefundUseCase;
	private final JsonConverter jsonConverter;

	// 판매자가 대회를 취소했다 (상품 서비스가 발행, key는 대회 ID). 지급 전 정산 내역은 닫고, 지급한 돈은 회수를 만든다
	// 이미 닫은 정산 내역과 이미 만든 회수는 건너뛰므로 같은 메시지를 다시 처리해도 안전하다
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
	public void handleRaceCancel(String payload) {
		RaceCancelEvent event = jsonConverter.fromJson(payload, RaceCancelEvent.class);
		payoutRaceCancelUseCase.cancelRace(event.raceId(), LocalDateTime.now(ZONE));
	}

	// 구매자에게 참가권을 환불했다 (주문 서비스가 발행, key는 주문 상품 ID). 1차 정산을 받은 참가권이면 회수를 남긴다
	// 같은 참가권으로 다시 와도 이미 만든 회수를 돌려주므로 같은 메시지를 다시 처리해도 안전하다
	@KafkaListener(topics = KafkaTopics.ORDER_ITEM_REFUNDED, groupId = GROUP_ID)
	@RetryableTopic(
		attempts = RETRY_ATTEMPTS,
		backOff = @BackOff(delay = RETRY_DELAY_MILLIS, multiplier = RETRY_MULTIPLIER),
		exclude = IllegalArgumentException.class,
		numPartitions = PARTITIONS,
		retryTopicSuffix = RETRY_TOPIC_SUFFIX,
		dltTopicSuffix = DLT_TOPIC_SUFFIX
	)
	public void handleItemRefunded(String payload) {
		OrderItemRefundedEvent event = jsonConverter.fromJson(payload, OrderItemRefundedEvent.class);
		payoutRefundUseCase.recordRefund(event, LocalDateTime.now(ZONE));
	}

	// 끝까지 실패한 메시지. 닫히지 않은 정산 내역이 지급될 수 있으므로 운영자가 확인해 직접 보류를 걸고 다시 보내야 한다
	@DltHandler
	public void handleDlt(String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
		log.error("Kafka 메시지를 끝내 처리하지 못했습니다. 확인 후 다시 보내야 합니다. topic={}, payload={}", topic, payload);
	}
}
