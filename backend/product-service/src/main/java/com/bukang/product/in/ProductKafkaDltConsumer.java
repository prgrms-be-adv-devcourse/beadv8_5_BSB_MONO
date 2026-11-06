package com.bukang.product.in;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.kafka.dlt.KafkaDltMessage;
import com.bukang.product.app.ProductFacade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 재시도를 모두 실패해 DLT(<토픽>.product.dlt)로 간 메시지를 받아 실패 테이블(PRODUCT_DLT_KAFKA_EVENT)에 남기고 Slack으로 알린다.
 * 자동으로 다시 처리하지 않는다. 원인을 고친 뒤 내부 API(/internal/v1/product/kafka-failures/{id}/resend)로 다시 보낸다.
 * 헤더(message-id, 예외 정보)를 읽어야 하므로 본문만이 아니라 ConsumerRecord로 받는다.
 * 테이블에 남기다 예외가 나면 기본 오류 처리기가 같은 메시지를 다시 시도하고, 끝내 실패하면 에러 로그를 남기고 건너뛴다
 * (이때도 메시지는 DLT 토픽과 아래 에러 로그에 남는다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductKafkaDltConsumer {
	// 본 리스너(product)와 오프셋을 따로 관리한다
	private static final String GROUP_ID = "product-dlt";
	private static final String MEMBER_JOINED_DLT = KafkaTopics.MEMBER_MEMBER_JOINED
		+ ProductKafkaConsumer.DLT_TOPIC_SUFFIX;
	private static final String MEMBER_MODIFIED_DLT = KafkaTopics.MEMBER_MODIFIED
		+ ProductKafkaConsumer.DLT_TOPIC_SUFFIX;
	private static final String SELLER_CHANGED_DLT = KafkaTopics.SELLER_CHANGED
		+ ProductKafkaConsumer.DLT_TOPIC_SUFFIX;
	private static final String HOLD_CANCEL_DLT = KafkaTopics.PRODUCT_HOLD_CANCEL
		+ ProductKafkaConsumer.DLT_TOPIC_SUFFIX;
	private static final String HOLD_RESTORE_DLT = KafkaTopics.PRODUCT_HOLD_RESTORE
		+ ProductKafkaConsumer.DLT_TOPIC_SUFFIX;

	private final ProductFacade productFacade;

	@KafkaListener(topics = MEMBER_JOINED_DLT, groupId = GROUP_ID)
	public void handleMemberJoinedDlt(ConsumerRecord<String, String> record) {
		log.error("처리하지 못한 회원가입 메시지: {}", record.value());
		record(record);
	}

	@KafkaListener(topics = MEMBER_MODIFIED_DLT, groupId = GROUP_ID)
	public void handleMemberModifiedDlt(ConsumerRecord<String, String> record) {
		log.error("처리하지 못한 회원 변경 메시지: {}", record.value());
		record(record);
	}

	@KafkaListener(topics = SELLER_CHANGED_DLT, groupId = GROUP_ID)
	public void handleSellerChangedDlt(ConsumerRecord<String, String> record) {
		log.error("처리하지 못한 판매자 메시지: {}", record.value());
		record(record);
	}

	// 해제하지 못한 자리는 결제 대기로 남아 남은 자리가 그만큼 적게 보인다. 원인을 고친 뒤 메시지를 다시 보내야 한다
	@KafkaListener(topics = HOLD_CANCEL_DLT, groupId = GROUP_ID)
	public void handleHoldCancelDlt(ConsumerRecord<String, String> record) {
		log.error("처리하지 못한 임시 확보 해제 메시지: {}", record.value());
		record(record);
	}

	// 되돌리지 못한 자리는 판매된 채 남아 정원보다 덜 팔린다. 원인을 고친 뒤 메시지를 다시 보내야 한다
	@KafkaListener(topics = HOLD_RESTORE_DLT, groupId = GROUP_ID)
	public void handleHoldRestoreDlt(ConsumerRecord<String, String> record) {
		log.error("처리하지 못한 판매 자리 복구 메시지: {}", record.value());
		record(record);
	}

	// DLT 레코드에서 원래 토픽·DLT 위치·message-id·예외를 꺼내 실패 테이블에 남긴다
	private void record(ConsumerRecord<String, String> record) {
		productFacade.recordDlt(KafkaDltMessage.from(record, ProductKafkaConsumer.DLT_TOPIC_SUFFIX));
	}
}
