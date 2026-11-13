package com.bukang.order.support;

import java.util.Comparator;
import java.util.List;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.order.event.OrderItemRefundedEvent;
import com.bukang.common.shared.order.event.RaceRefundEvent;
import com.bukang.order.domain.OrderOutboxKafkaEvent;
import com.bukang.order.out.OrderOutboxKafkaEventRepository;

/**
 * 주문 아웃박스에 남은 자리 메시지(복구·해제)와 환불 알림을 참가 신청 ID로 남긴 순서대로 읽는다.
 * 테스트에서는 릴레이가 돌지 않으므로, 커밋된 취소·만료가 상품 서비스에 보낼 메시지를 이 행으로 확인한다.
 * 같은 H2 DB를 여러 테스트 클래스가 함께 쓰므로, 이 도우미를 쓰는 테스트는 앞뒤로 아웃박스를 비운다.
 */
public final class SeatOutboxMessages {
	private SeatOutboxMessages() {
	}

	// 판매 자리 복구 메시지 (product.hold.restore)
	public static List<Long> restored(OrderOutboxKafkaEventRepository repository) {
		return participationIds(repository, KafkaTopics.PRODUCT_HOLD_RESTORE);
	}

	// 결제 안 한 자리 해제 메시지 (product.hold.cancel)
	public static List<Long> released(OrderOutboxKafkaEventRepository repository) {
		return participationIds(repository, KafkaTopics.PRODUCT_HOLD_CANCEL);
	}

	// 정산에 보낼 환불 알림 (queuing.orderitem.refunded, BUK-74). 남긴 순서대로 읽는다
	public static List<OrderItemRefundedEvent> refunded(OrderOutboxKafkaEventRepository repository,
		JsonConverter jsonConverter) {
		return repository.findAll().stream()
			.filter(event -> KafkaTopics.ORDER_ITEM_REFUNDED.equals(event.getTopic()))
			.sorted(Comparator.comparing(OrderOutboxKafkaEvent::getId))
			.map(event -> jsonConverter.fromJson(event.getPayload(), OrderItemRefundedEvent.class))
			.toList();
	}

	// 정산에 보낼 대회 취소 환불 보류·완료 (queuing.order.racerefund, 11/05 회의 R1). 남긴 순서대로 읽는다
	public static List<RaceRefundEvent> raceRefunds(OrderOutboxKafkaEventRepository repository,
		JsonConverter jsonConverter) {
		return repository.findAll().stream()
			.filter(event -> KafkaTopics.ORDER_RACE_REFUND.equals(event.getTopic()))
			.sorted(Comparator.comparing(OrderOutboxKafkaEvent::getId))
			.map(event -> jsonConverter.fromJson(event.getPayload(), RaceRefundEvent.class))
			.toList();
	}

	private static List<Long> participationIds(OrderOutboxKafkaEventRepository repository, String topic) {
		return repository.findAll().stream()
			.filter(event -> topic.equals(event.getTopic()))
			.sorted(Comparator.comparing(OrderOutboxKafkaEvent::getId))
			.map(OrderOutboxKafkaEvent::getRefId)
			.toList();
	}
}
