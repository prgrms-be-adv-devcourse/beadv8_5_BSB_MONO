package com.bukang.member.app;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.member.event.MarketingConsentChangedEvent;
import com.bukang.common.shared.member.event.MemberJoinedEvent;
import com.bukang.common.shared.member.event.MemberModifiedEvent;
import com.bukang.common.shared.member.event.MemberWithdrawnEvent;
import com.bukang.common.shared.seller.event.SellerChangedEvent;
import com.bukang.member.domain.Member;
import com.bukang.member.domain.MemberOutboxEvent;
import com.bukang.member.domain.Seller;
import com.bukang.member.out.MemberOutboxEventRepository;

import lombok.RequiredArgsConstructor;

/**
 * 회원·판매자 엔티티가 발행한 스프링 이벤트를 받아 아웃박스 테이블에 저장한다 (BUK-41). 보내는 일은 MemberOutboxRelayUseCase가 한다.
 * 일반 @EventListener라 발행한 그 자리에서, 회원 데이터와 같은 트랜잭션으로 저장된다.
 * MANDATORY: 트랜잭션 밖에서 발행하면 조용히 버리지 않고 예외를 낸다.
 * 대상 ID는 본문에서 꺼내, Kafka key·헤더·본문의 대상 ID가 늘 같다
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class MemberOutboxAppendUseCase {
	private static final String MEMBER = Member.class.getSimpleName();
	private static final String SELLER = Seller.class.getSimpleName();

	private final MemberOutboxEventRepository memberOutboxEventRepository;
	private final JsonConverter jsonConverter;

	@EventListener
	public void appendMemberJoined(MemberJoinedEvent event) {
		append(KafkaTopics.MEMBER_MEMBER_JOINED, MEMBER, event.id(), event.eventId(), event);
	}

	@EventListener
	public void appendMemberModified(MemberModifiedEvent event) {
		append(KafkaTopics.MEMBER_MODIFIED, MEMBER, event.id(), event.eventId(), event);
	}

	@EventListener
	public void appendMemberWithdrawn(MemberWithdrawnEvent event) {
		append(KafkaTopics.MEMBER_WITHDRAWN, MEMBER, event.id(), event.eventId(), event);
	}

	@EventListener
	public void appendMarketingConsentChanged(MarketingConsentChangedEvent event) {
		append(KafkaTopics.MARKETING_CONSENT_CHANGED, MEMBER, event.memberId(), event.eventId(), event);
	}

	@EventListener
	public void appendSellerChanged(SellerChangedEvent event) {
		append(KafkaTopics.SELLER_CHANGED, SELLER, event.id(), event.eventId(), event);
	}

	// 이벤트 ID가 비면(받는 쪽용 옛 생성자) 받는 쪽이 중복을 거르지 못하므로 막는다. 예외로 회원 데이터도 롤백된다
	// 발생 시각은 DB가 마이크로초까지 저장하므로 저장 전에 맞춰, 헤더 값과 행 값이 같게 한다
	private void append(String topic, String refType, long refId, String eventId, Object event) {
		if (eventId == null || eventId.isBlank()) {
			throw new IllegalArgumentException("이벤트 ID가 없는 이벤트는 아웃박스에 저장할 수 없습니다. topic=" + topic);
		}
		memberOutboxEventRepository.save(MemberOutboxEvent.pending(eventId, topic, refType, refId,
			jsonConverter.toJson(event), LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)));
	}
}
