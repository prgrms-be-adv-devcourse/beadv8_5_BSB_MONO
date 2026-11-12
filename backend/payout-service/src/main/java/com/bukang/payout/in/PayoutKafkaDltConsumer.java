package com.bukang.payout.in;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.kafka.dlt.KafkaDltMessage;
import com.bukang.payout.app.PayoutDltFacade;
import com.bukang.payout.app.PayoutKafkaRetry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 재시도를 모두 실패해 DLT(<토픽>.payout.dlt)로 간 메시지를 받아 실패 테이블(PAYOUT_DLT_KAFKA_EVENT)에 남기고 Slack으로 알린다.
 * 자동으로 다시 처리하지 않는다. 원인을 고친 뒤 내부 API(/internal/v1/payout/kafka-failures/{id}/resend)로 다시 보낸다.
 * 헤더(message-id, 예외 정보)를 읽어야 하므로 본문만이 아니라 ConsumerRecord로 받는다 (ProductKafkaDltConsumer와 같은 방식).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutKafkaDltConsumer {
	// 본 리스너(payout)와 오프셋을 따로 관리한다
	private static final String GROUP_ID = "payout-dlt";
	private static final String RACE_CANCEL_DLT = KafkaTopics.ORDER_RACE_CANCEL + PayoutKafkaRetry.DLT_TOPIC_SUFFIX;
	private static final String ITEM_REFUNDED_DLT = KafkaTopics.ORDER_ITEM_REFUNDED + PayoutKafkaRetry.DLT_TOPIC_SUFFIX;
	private static final String CLAIM_DEPOSITED_DLT = KafkaTopics.CLAIM_DEPOSITED + PayoutKafkaRetry.DLT_TOPIC_SUFFIX;

	private final PayoutDltFacade payoutDltFacade;

	// 닫히지 않은 정산 내역이 지급될 수 있다. 원인을 고치기 전이면 그 대회의 정산 내역에 직접 지급보류를 건다
	@KafkaListener(topics = RACE_CANCEL_DLT, groupId = GROUP_ID)
	public void handleRaceCancelDlt(ConsumerRecord<String, String> record) {
		log.error("처리하지 못한 대회 취소 메시지: {}", record.value());
		record(record);
	}

	// 1차 정산을 받은 참가권의 회수가 생기지 않았다. 원인을 고친 뒤 다시 보낸다
	@KafkaListener(topics = ITEM_REFUNDED_DLT, groupId = GROUP_ID)
	public void handleItemRefundedDlt(ConsumerRecord<String, String> record) {
		log.error("처리하지 못한 참가권 환불 메시지: {}", record.value());
		record(record);
	}

	// 판매자는 돈을 냈는데 회수가 끝나지 않았다. 입금액과 청구를 맞춰 보고 다시 보내거나 관리자 입금 확인으로 끝낸다
	@KafkaListener(topics = CLAIM_DEPOSITED_DLT, groupId = GROUP_ID)
	public void handleClaimDepositedDlt(ConsumerRecord<String, String> record) {
		log.error("처리하지 못한 청구 입금 메시지: {}", record.value());
		record(record);
	}

	// DLT 레코드에서 원래 토픽·DLT 위치·message-id·예외를 꺼내 실패 테이블에 남긴다
	private void record(ConsumerRecord<String, String> record) {
		payoutDltFacade.recordDlt(KafkaDltMessage.from(record, PayoutKafkaRetry.DLT_TOPIC_SUFFIX));
	}
}
