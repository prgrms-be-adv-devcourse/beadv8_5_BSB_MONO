package com.bukang.member.recordcert.in;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.kafka.dlt.KafkaDltMessage;
import com.bukang.common.shared.order.event.OrderItemRefundedEvent;
import com.bukang.common.shared.recordcert.event.RecordCertCheckedEvent;
import com.bukang.member.app.MemberKafkaRetry;
import com.bukang.member.config.MemberKafkaConfig;
import com.bukang.member.recordcert.app.RecordCertFacade;

import lombok.RequiredArgsConstructor;

/**
 * ai-service가 보낸 기록증 서버 확인 결과(BUK-275)와 order가 보낸 참가권 환불 알림(BUK-277)을 받는다. 리스너는 퍼사드를 부르기만 한다
 * 처리 중 예외(product 응답 없음, 낙관적 잠금 충돌)는 MemberKafkaConfig가 다시 시도하고, 끝내 실패하면 실패 토픽으로 보낸다.
 * 실패 토픽의 결과는 그 제출을 심사 필요로 넘긴다
 */
@Component
@RequiredArgsConstructor
public class RecordCertKafkaListener {
	// 바꾸면 새 그룹으로 보고 토픽을 처음부터 다시 읽는다 (auto-offset-reset: earliest)
	private static final String GROUP_ID = "member";
	private static final String CHECKED_DLT = KafkaTopics.RECORD_CERT_CHECKED + MemberKafkaRetry.DLT_TOPIC_SUFFIX;
	private static final String REFUNDED_DLT = KafkaTopics.ORDER_ITEM_REFUNDED + MemberKafkaRetry.DLT_TOPIC_SUFFIX;

	private final RecordCertFacade recordCertFacade;
	private final JsonConverter jsonConverter;

	@KafkaListener(topics = KafkaTopics.RECORD_CERT_CHECKED, groupId = GROUP_ID,
		containerFactory = MemberKafkaConfig.LISTENER_FACTORY)
	public void handleChecked(String payload) {
		recordCertFacade.applyCheckResult(jsonConverter.fromJson(payload, RecordCertCheckedEvent.class));
	}

	// 실패 토픽 처리에서 난 예외는 기본 오류 처리(몇 번 다시 시도한 뒤 로그)에 맡긴다. 메시지는 실패 토픽에 남는다
	@KafkaListener(topics = CHECKED_DLT, groupId = MemberKafkaRetry.DLT_GROUP_ID)
	public void handleCheckedDlt(ConsumerRecord<String, String> record) {
		recordCertFacade.handleCheckResultDlt(KafkaDltMessage.from(record, MemberKafkaRetry.DLT_TOPIC_SUFFIX));
	}

	// 정산(group "payout")과 그룹이 달라 같은 환불 알림을 따로 받는다. 낙관적 잠금 충돌이면 다시 시도할 때 다시 읽어 닫는다
	@KafkaListener(topics = KafkaTopics.ORDER_ITEM_REFUNDED, groupId = GROUP_ID,
		containerFactory = MemberKafkaConfig.LISTENER_FACTORY)
	public void handleItemRefunded(String payload) {
		recordCertFacade.closeByRefund(jsonConverter.fromJson(payload, OrderItemRefundedEvent.class));
	}

	@KafkaListener(topics = REFUNDED_DLT, groupId = MemberKafkaRetry.DLT_GROUP_ID)
	public void handleItemRefundedDlt(ConsumerRecord<String, String> record) {
		recordCertFacade.handleRefundedDlt(KafkaDltMessage.from(record, MemberKafkaRetry.DLT_TOPIC_SUFFIX));
	}
}
