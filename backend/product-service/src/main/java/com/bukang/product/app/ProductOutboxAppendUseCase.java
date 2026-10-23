package com.bukang.product.app;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.product.domain.ProductOutboxEvent;
import com.bukang.product.domain.Race;
import com.bukang.product.out.ProductOutboxEventRepository;

import lombok.RequiredArgsConstructor;

/**
 * 보낼 Kafka 메시지를 아웃박스 테이블에 저장한다. 보내는 일은 ProductOutboxRelayUseCase가 한다.
 * MANDATORY: 대회를 저장하는 트랜잭션 안에서만 부를 수 있다 (트랜잭션 밖에서 부르면 예외).
 * 따로 커밋하면 대회 저장이 롤백돼도 메시지가 남거나, 메시지 저장만 실패할 수 있기 때문이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ProductOutboxAppendUseCase {
	// 대회 메시지의 대상 종류. 엔티티의 getModelTypeCode()와 같은 값(클래스 이름)이다
	private static final String RACE_REF_TYPE = Race.class.getSimpleName();

	private final ProductOutboxEventRepository productOutboxEventRepository;
	private final JsonConverter jsonConverter;

	// 대회 ID를 key로 보낼 메시지를 남긴다. topic은 KafkaTopics 상수다
	public void appendRaceEvent(String topic, int raceId, Object event) {
		String payload = jsonConverter.toJson(event);
		productOutboxEventRepository.save(ProductOutboxEvent.pending(RACE_REF_TYPE, raceId, topic, payload));
	}
}
