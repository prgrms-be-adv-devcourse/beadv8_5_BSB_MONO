package com.bukang.order.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.order.out.OrderInboxKafkaEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 받은 Kafka 메시지를 인박스로 거른다 (BUK-53)
 * 상품 인박스와 달리 기록을 처리와 한 트랜잭션에 묶지 않는다. 대회 취소 환불은 주문마다 따로 커밋하고 예치금 서비스를 부르므로
 * 한 트랜잭션이 아니다. 그래서 처리가 모두 성공한 뒤에 기록하고, 처리 전에 기록이 있으면 건너뛴다
 * 기록 전에 같은 메시지가 동시에 오면 둘 다 처리될 수 있지만, 환불 흐름이 주문 ID로 멱등이라 두 번 환불되지는 않는다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderInboxUseCase {
	private final OrderInboxKafkaEventRepository orderInboxKafkaEventRepository;

	// 이미 처리한 메시지면 true. messageId가 없으면(헤더 없이 보낸 메시지) 거를 기준이 없어 처리한다
	@Transactional(readOnly = true)
	public boolean alreadyProcessed(String messageId, String consumerGroup, String topic) {
		if (messageId == null) {
			log.warn("message-id 헤더가 없는 메시지를 인박스 없이 처리합니다. group={}, topic={}", consumerGroup, topic);
			return false;
		}
		boolean processed = orderInboxKafkaEventRepository.existsByConsumerGroupAndMessageId(consumerGroup, messageId);
		if (processed) {
			log.info("이미 처리한 메시지를 건너뜁니다. messageId={}, group={}, topic={}", messageId, consumerGroup, topic);
		}
		return processed;
	}

	@Transactional
	public void record(String messageId, String consumerGroup, String topic, LocalDateTime now) {
		if (messageId != null) {
			orderInboxKafkaEventRepository.insertIgnore(messageId, consumerGroup, topic, now);
		}
	}
}
