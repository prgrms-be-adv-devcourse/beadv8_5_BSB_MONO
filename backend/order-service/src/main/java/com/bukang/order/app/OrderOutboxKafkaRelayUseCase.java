package com.bukang.order.app;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.common.shared.kafka.outbox.OutboxKafkaRelay;
import com.bukang.common.shared.kafka.outbox.OutboxStatus;
import com.bukang.order.domain.OrderOutboxKafkaEvent;
import com.bukang.order.domain.exception.OutboxEventNotFailedException;
import com.bukang.order.domain.exception.OutboxEventNotFoundException;
import com.bukang.order.out.OrderOutboxKafkaEventRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 주문 아웃박스의 보낼 차례 행을 Kafka에 보낸다. 보내는 방식(순서·재시도·포기)은 common OutboxKafkaRelay에 있다 (BUK-207)
 * Kafka 연결 장애로는 포기하지 않아, 브로커가 돌아오면 밀린 자리 해제·복구·환불 알림이 나간다.
 * 메시지 자체의 문제로 포기(발행 실패)하면 자리가 묶이거나(해제) 정원으로 돌아오지 않는다(복구).
 * 정원보다 덜 팔릴 뿐 더 팔리지는 않지만, 에러 로그를 보고 사람이 원인을 고친 뒤 아래 retry로 되돌린다 (BUK-180)
 */
@Slf4j
@Service
public class OrderOutboxKafkaRelayUseCase extends OutboxKafkaRelay<OrderOutboxKafkaEvent> {
	private final OrderOutboxKafkaEventRepository orderOutboxKafkaEventRepository;

	public OrderOutboxKafkaRelayUseCase(OrderOutboxKafkaEventRepository orderOutboxKafkaEventRepository,
		KafkaTemplate<String, String> kafkaTemplate) {
		super(orderOutboxKafkaEventRepository, kafkaTemplate);
		this.orderOutboxKafkaEventRepository = orderOutboxKafkaEventRepository;
	}

	// 발행 실패 행을 저장 순서대로 limit건 (내부 API 목록, BUK-180)
	@Transactional(readOnly = true)
	public List<OrderOutboxKafkaEvent> findSendFailed(int limit) {
		return orderOutboxKafkaEventRepository.findListByStatus(OutboxStatus.SEND_FAILED, Limit.of(limit));
	}

	// 원인을 고친 뒤 발행 실패 행을 보낼 차례로 되돌린다 (내부 API, BUK-180). 다음 릴레이(1초 뒤)가 보낸다
	// 그사이 같은 대상의 뒤 메시지가 먼저 나갔을 수 있어 받는 쪽에는 순서가 바뀌어 도착한다 (상품의 복구·해제는 멱등)
	// 조건부 UPDATE라 동시에 두 번 불러도 한 번만 통과하고, 릴레이가 이미 보낸(SENT) 행을 덮지 않는다 (PR #436 리뷰)
	@Transactional
	public OrderOutboxKafkaEvent retry(long id, LocalDateTime now) {
		if (orderOutboxKafkaEventRepository.resetSendFailed(id, now) == 0) {
			throw new OutboxEventNotFailedException(id, findOutbox(id).getStatus());
		}
		OrderOutboxKafkaEvent event = findOutbox(id);
		log.info("발행 실패 아웃박스 메시지를 다시 보내도록 되돌렸습니다. id={}, topic={}, key={}",
			event.getId(), event.getTopic(), event.getRefId());
		return event;
	}

	private OrderOutboxKafkaEvent findOutbox(long id) {
		return orderOutboxKafkaEventRepository.findById(id).orElseThrow(() -> new OutboxEventNotFoundException(id));
	}
}
