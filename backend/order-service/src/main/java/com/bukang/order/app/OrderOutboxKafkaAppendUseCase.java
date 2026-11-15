package com.bukang.order.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.order.event.OrderItemRefundReason;
import com.bukang.common.shared.order.event.OrderItemRefundedEvent;
import com.bukang.common.shared.order.event.RaceRefundEvent;
import com.bukang.common.shared.order.event.RaceRefundKind;
import com.bukang.common.shared.race.event.HoldCancelEvent;
import com.bukang.common.shared.race.event.HoldRestoreEvent;
import com.bukang.order.domain.OrderItem;
import com.bukang.order.domain.OrderOutboxKafkaEvent;
import com.bukang.order.domain.Participation;
import com.bukang.order.out.OrderOutboxKafkaEventRepository;

import lombok.RequiredArgsConstructor;

/**
 * 상품 서비스에 보낼 자리 메시지(복구·해제)와 정산에 보낼 환불 알림·대회 보류·완료를 아웃박스 테이블에 저장한다. 보내는 일은 OrderOutboxKafkaRelayUseCase가 한다.
 * MANDATORY: 취소·만료 트랜잭션 안에서만 부를 수 있다 (트랜잭션 밖에서 부르면 예외).
 * 따로 커밋하면 취소가 롤백돼도 메시지가 남아(예: 환불 실패) 결제 완료 주문의 자리가 다시 팔리거나,
 * 취소는 커밋됐는데 메시지만 사라질 수 있기 때문이다.
 * 자리 메시지의 key는 참가 신청 ID다 (상품 서비스가 자리를 참가 신청 ID로 찾는다). 환불 알림의 key는 주문 상품 ID, 대회 보류·완료의 key는 대회 ID다.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class OrderOutboxKafkaAppendUseCase {
	// 자리 메시지의 대상 종류. 엔티티의 getModelTypeCode()와 같은 값(클래스 이름)이다
	private static final String PARTICIPATION_REF_TYPE = Participation.class.getSimpleName();
	private static final String ORDER_ITEM_REF_TYPE = OrderItem.class.getSimpleName();
	// 대회는 상품 서비스의 대상이라 주문에 엔티티가 없다. 대상 종류만 이름으로 적는다
	private static final String RACE_REF_TYPE = "Race";

	private final OrderOutboxKafkaEventRepository orderOutboxKafkaEventRepository;
	private final JsonConverter jsonConverter;

	// 판매한 자리를 정원으로 되돌려 달라고 남긴다 (product.hold.restore)
	public void appendSeatRestore(long participationId) {
		append(KafkaTopics.PRODUCT_HOLD_RESTORE, participationId, new HoldRestoreEvent(participationId));
	}

	// 결제하지 않은 자리를 풀어 달라고 남긴다 (product.hold.cancel)
	public void appendSeatRelease(long participationId) {
		append(KafkaTopics.PRODUCT_HOLD_CANCEL, participationId, new HoldCancelEvent(participationId));
	}

	/**
	 * 참가권을 환불했다고 정산에 알린다 (queuing.orderitem.refunded, BUK-74). 정산은 1차 정산을 받은 참가권이면 회수를 남긴다
	 * 주문 상품을 취소로 확정하는 트랜잭션에서 부른다. 이미 취소한 상품은 부르는 쪽이 건너뛰므로 같은 환불로 두 번 남지 않는다
	 */
	public void appendItemRefunded(OrderItem item, long buyerId, LocalDateTime refundedAt,
		OrderItemRefundReason reason) {
		OrderItemRefundedEvent event = new OrderItemRefundedEvent(item.getId(), item.getRaceId(),
			item.getRaceCourseId(), item.getAmount(), refundedAt, reason, buyerId);
		append(KafkaTopics.ORDER_ITEM_REFUNDED, ORDER_ITEM_REF_TYPE, item.getId(), event);
	}

	// 대회 취소 환불을 시작한다고 정산에 알린다. 정산은 그 대회 지급을 막는다 (11/05 회의 R1)
	public void appendRaceHeld(long raceId, LocalDateTime heldAt) {
		append(KafkaTopics.ORDER_RACE_REFUND, RACE_REF_TYPE, raceId,
			new RaceRefundEvent(RaceRefundKind.HELD, raceId, 0, heldAt));
	}

	// 대회의 결제한 참가권을 실패 없이 모두 환불했다고 정산에 알린다. 정산은 보류를 푼다. 회수는 참가권마다 appendItemRefunded로 한다 (BUK-76)
	public void appendRaceRefunded(long raceId, long refundedCount, LocalDateTime refundedAt) {
		append(KafkaTopics.ORDER_RACE_REFUND, RACE_REF_TYPE, raceId,
			new RaceRefundEvent(RaceRefundKind.REFUNDED, raceId, refundedCount, refundedAt));
	}

	private void append(String topic, long participationId, Object event) {
		append(topic, PARTICIPATION_REF_TYPE, participationId, event);
	}

	private void append(String topic, String refType, long refId, Object event) {
		String payload = jsonConverter.toJson(event);
		orderOutboxKafkaEventRepository.save(OrderOutboxKafkaEvent.pending(refType, refId, topic, payload));
	}
}
