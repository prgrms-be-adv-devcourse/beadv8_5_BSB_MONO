package com.bukang.order.app;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.race.event.HoldCancelEvent;
import com.bukang.common.shared.race.event.HoldRestoreEvent;
import com.bukang.order.domain.OrderOutboxKafkaEvent;
import com.bukang.order.domain.Participation;
import com.bukang.order.out.OrderOutboxKafkaEventRepository;

import lombok.RequiredArgsConstructor;

/**
 * 상품 서비스에 보낼 자리 메시지(복구·해제)를 아웃박스 테이블에 저장한다. 보내는 일은 OrderOutboxKafkaRelayUseCase가 한다.
 * MANDATORY: 취소·만료 트랜잭션 안에서만 부를 수 있다 (트랜잭션 밖에서 부르면 예외).
 * 따로 커밋하면 취소가 롤백돼도 메시지가 남아(예: 환불 실패) 결제 완료 주문의 자리가 다시 팔리거나,
 * 취소는 커밋됐는데 메시지만 사라질 수 있기 때문이다.
 * 메시지 key는 참가 신청 ID다 (상품 서비스가 자리를 참가 신청 ID로 찾는다).
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class OrderOutboxKafkaAppendUseCase {
	// 자리 메시지의 대상 종류. 엔티티의 getModelTypeCode()와 같은 값(클래스 이름)이다
	private static final String PARTICIPATION_REF_TYPE = Participation.class.getSimpleName();

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

	private void append(String topic, long participationId, Object event) {
		String payload = jsonConverter.toJson(event);
		orderOutboxKafkaEventRepository.save(
			OrderOutboxKafkaEvent.pending(PARTICIPATION_REF_TYPE, participationId, topic, payload));
	}
}
