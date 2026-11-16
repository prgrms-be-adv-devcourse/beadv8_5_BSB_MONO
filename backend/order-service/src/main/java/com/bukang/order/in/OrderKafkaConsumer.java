package com.bukang.order.in;

import java.time.LocalDateTime;

import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.KafkaMessageId;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.race.event.RaceCancelEvent;
import com.bukang.order.app.CrewMemberLeftUseCase;
import com.bukang.order.app.OrderDltUseCase;
import com.bukang.order.app.OrderInboxUseCase;
import com.bukang.order.app.OrderKafkaRetry;
import com.bukang.order.app.RaceCancelRefundUseCase;

import lombok.RequiredArgsConstructor;

/**
 * 다른 서비스가 보낸 Kafka 메시지를 받는다. 메시지는 JSON 문자열이고 JsonConverter로 바꾼다
 * 처리에 실패하면 재시도 토픽으로 보내 간격을 늘려 가며 다시 하고, 그래도 실패하면 <토픽>.order.dlt로 보낸다
 * DLT 메시지는 OrderKafkaDltConsumer가 받아 실패 표에 남기고 Slack으로 알린다 (BUK-180)
 */
@Component
@RequiredArgsConstructor
public class OrderKafkaConsumer {
	// application.yaml에 group-id가 없으므로 리스너마다 적는다
	private static final String GROUP_ID = "order";
	// 재시도·DLT 토픽 파티션 수 (기본값은 1개). 대회 취소는 드물어 1개로 충분하다
	private static final String PARTITIONS = "1";
	// 처음 시도를 포함한 시도 횟수. 예치금 서비스가 잠깐 내려가도 버티게 10초 → 30초 → 90초 간격으로 다시 한다
	private static final String RETRY_ATTEMPTS = "4";
	// 첫 재시도 지연과 재시도·DLT 토픽 접미사는 OrderKafkaRetry에 있다. 다시 보내기(OrderDltUseCase)가 같은 값으로
	// 첫 재시도 토픽 이름(예: order.race.cancel.order.retry-10000)을 만든다
	private static final long RETRY_DELAY_MILLIS = OrderKafkaRetry.FIRST_RETRY_DELAY_MILLIS;
	private static final double RETRY_MULTIPLIER = 3.0;
	private static final String RETRY_TOPIC_SUFFIX = OrderKafkaRetry.RETRY_TOPIC_SUFFIX;
	private static final String DLT_TOPIC_SUFFIX = OrderKafkaRetry.DLT_TOPIC_SUFFIX;
	// DLT는 OrderKafkaDltConsumer가 받으므로, @RetryableTopic이 기본으로 띄우는 DLT 리스너는 시작하지 않는다
	// (켜 두면 같은 DLT 메시지를 두 곳에서 받는다)
	private static final String AUTO_START_DEFAULT_DLT_HANDLER = "false";

	private final RaceCancelRefundUseCase raceCancelRefundUseCase;
	private final OrderInboxUseCase orderInboxUseCase;
	private final CrewMemberLeftUseCase crewMemberLeftUseCase;
	private final OrderDltUseCase orderDltUseCase;
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
		dltTopicSuffix = DLT_TOPIC_SUFFIX,
		autoStartDltHandler = AUTO_START_DEFAULT_DLT_HANDLER
	)
	public void handleRaceCancel(
		String payload,
		@Header(name = KafkaMessageId.HEADER, required = false) String messageId
	) {
		// 같은 메시지를 다시 받으면(재전달) 대회의 주문을 다시 훑고 예치금 서비스를 다시 부르지 않게 건너뛴다 (BUK-53)
		if (orderInboxUseCase.alreadyProcessed(messageId, GROUP_ID, KafkaTopics.ORDER_RACE_CANCEL)) {
			orderDltUseCase.completeByResend(messageId, LocalDateTime.now());
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
		orderDltUseCase.completeByResend(messageId, LocalDateTime.now());
	}

	// 크루원이 크루에서 빠졌다 (회원 서비스가 발행, key는 크루 ID, 본문 CrewMemberLeftEvent). 그 크루의 모집 중 파티에서 뺀다 (BUK-77)
	@KafkaListener(topics = KafkaTopics.CREW_MEMBER_LEFT, groupId = GROUP_ID)
	@RetryableTopic(
		attempts = RETRY_ATTEMPTS,
		backOff = @BackOff(delay = RETRY_DELAY_MILLIS, multiplier = RETRY_MULTIPLIER),
		exclude = IllegalArgumentException.class,
		numPartitions = PARTITIONS,
		retryTopicSuffix = RETRY_TOPIC_SUFFIX,
		dltTopicSuffix = DLT_TOPIC_SUFFIX,
		autoStartDltHandler = AUTO_START_DEFAULT_DLT_HANDLER
	)
	public void handleCrewMemberLeft(
		String payload,
		@Header(name = KafkaMessageId.HEADER, required = false) String messageId
	) {
		if (orderInboxUseCase.alreadyProcessed(messageId, GROUP_ID, KafkaTopics.CREW_MEMBER_LEFT)) {
			orderDltUseCase.completeByResend(messageId, LocalDateTime.now());
			return;
		}
		CrewMemberLeft event = jsonConverter.fromJson(payload, CrewMemberLeft.class);
		// 칸이 빠지면 0·null로 읽혀 아무 파티도 못 찾고 처리됨으로 남는다. 다시 해도 안 되므로 바로 DLT로 보낸다
		if (event.crewId() <= 0 || event.memberId() <= 0 || event.joinedAt() == null) {
			throw new IllegalArgumentException("크루 떠남 메시지에 crewId·memberId·joinedAt이 없습니다. payload=" + payload);
		}
		crewMemberLeftUseCase.removeFromParties(event.crewId(), event.memberId(), event.joinedAt(),
			LocalDateTime.now());
		orderInboxUseCase.record(messageId, GROUP_ID, KafkaTopics.CREW_MEMBER_LEFT, LocalDateTime.now());
		orderDltUseCase.completeByResend(messageId, LocalDateTime.now());
	}

	// CrewMemberLeftEvent 중 쓰는 칸만 읽는다. 이유(reason)는 늘어날 수 있어 읽지 않는다. 모르는 값이 와도 실패하지 않게 (#311 계약)
	record CrewMemberLeft(long crewId, long memberId, LocalDateTime joinedAt) {
	}
}
