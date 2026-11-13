package com.bukang.order.in;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.kafka.dlt.KafkaDltMessage;
import com.bukang.order.app.OrderKafkaFailureFacade;
import com.bukang.order.app.OrderKafkaRetry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 재시도를 모두 실패해 DLT(<토픽>.order.dlt)로 간 메시지를 받아 실패 표(ORDER_DLT_KAFKA_EVENT)에 남기고 Slack으로 알린다 (BUK-180).
 * 상품 ProductKafkaDltConsumer와 같은 방식이다. 자동으로 다시 처리하지 않는다.
 * 원인을 고친 뒤 내부 API(/internal/v1/order/kafka-failures/{id}/resend)로 다시 보낸다.
 * 헤더(message-id, 예외 정보)를 읽어야 하므로 본문만이 아니라 ConsumerRecord로 받는다.
 * 표에 남기다 예외가 나면 기본 오류 처리기가 같은 메시지를 다시 시도하고, 끝내 실패하면 에러 로그를 남기고 건너뛴다
 * (이때도 메시지는 DLT 토픽과 아래 에러 로그에 남는다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderKafkaDltConsumer {
	// 본 리스너(order)와 오프셋을 따로 관리한다
	private static final String GROUP_ID = "order-dlt";
	private static final String RACE_CANCEL_DLT = KafkaTopics.ORDER_RACE_CANCEL + OrderKafkaRetry.DLT_TOPIC_SUFFIX;
	private static final String CREW_MEMBER_LEFT_DLT = KafkaTopics.CREW_MEMBER_LEFT + OrderKafkaRetry.DLT_TOPIC_SUFFIX;

	private final OrderKafkaFailureFacade orderKafkaFailureFacade;

	// 환불하지 못한 참가권이 남아 있다. 예치금 서비스 등 원인을 고친 뒤 다시 보내면 실패한 주문만 다시 환불한다
	@KafkaListener(topics = RACE_CANCEL_DLT, groupId = GROUP_ID)
	public void handleRaceCancelDlt(ConsumerRecord<String, String> record) {
		log.error("처리하지 못한 대회 취소 환불 메시지: {}", record.value());
		record(record);
	}

	// 크루를 나간 사람이 모집 중 파티에 남아 있다. 원인을 고친 뒤 다시 보내야 한다
	@KafkaListener(topics = CREW_MEMBER_LEFT_DLT, groupId = GROUP_ID)
	public void handleCrewMemberLeftDlt(ConsumerRecord<String, String> record) {
		log.error("처리하지 못한 크루 떠남 메시지: {}", record.value());
		record(record);
	}

	// DLT 레코드에서 원래 토픽·DLT 위치·message-id·예외를 꺼내 실패 표에 남긴다
	private void record(ConsumerRecord<String, String> record) {
		orderKafkaFailureFacade.recordDlt(KafkaDltMessage.from(record, OrderKafkaRetry.DLT_TOPIC_SUFFIX));
	}
}
