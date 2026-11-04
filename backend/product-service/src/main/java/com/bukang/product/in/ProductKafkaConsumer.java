package com.bukang.product.in;

import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.KafkaMessageId;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.member.event.MemberJoinedEvent;
import com.bukang.common.shared.member.event.MemberModifiedEvent;
import com.bukang.common.shared.race.event.HoldCancelEvent;
import com.bukang.common.shared.race.event.HoldRestoreEvent;
import com.bukang.common.shared.seller.event.SellerChangedEvent;
import com.bukang.product.app.ProductFacade;

import lombok.RequiredArgsConstructor;

/**
 * 회원·판매자 이벤트를 받아 복제본(ProductMember, ProductSeller)에 저장하고, 주문 서비스의 임시 확보 해제·판매 자리 복구 요청을 처리한다
 * 메시지는 JSON 문자열로 받아 JsonConverter로 바꾼다.
 * 처리 중 예외가 나면 재시도 토픽으로 보내 간격을 늘려 가며 다시 시도하고, 그래도 실패하면 <토픽>.product.dlt로 보낸다.
 * DLT 메시지는 ProductKafkaDltConsumer가 받는다.
 * message-id 헤더가 있으면 인박스로 같은 메시지를 두 번 처리하지 않는다 (재시도·DLT 토픽으로 넘어가도 헤더는 그대로다).
 */
@Component
@RequiredArgsConstructor
public class ProductKafkaConsumer {
	// application.yaml에 group-id가 없으므로 리스너마다 적는다. 인박스에 남기는 그룹(ProductFacade.KAFKA_GROUP_ID)과 같은 값이다
	private static final String GROUP_ID = "product";
	// 토픽 파티션 수 (KafkaTopicConfig.PARTITIONS와 맞춘다). 재시도·DLT 토픽도 같은 수로 만든다 (기본값은 1개)
	private static final String PARTITIONS = "1";
	// 동시에 처리할 쓰레드 수 (재시도·DLT 리스너도 같은 수). 지금은 메시지가 적어 1개로 충분하다
	// 파티션 수까지만 효과가 있으므로, 늘릴 때는 PARTITIONS(KafkaTopicConfig 포함)와 함께 올린다
	private static final String CONCURRENCY = "1";
	// 처음 시도를 포함한 시도 횟수. 실패하면 1초 → 2초 간격(2배수)으로 2번 다시 시도한다
	private static final String RETRY_ATTEMPTS = "3";
	private static final long RETRY_DELAY_MILLIS = 1000L;
	private static final double RETRY_MULTIPLIER = 2.0;
	// 재시도·DLT 토픽 접미사에 서비스 이름을 넣는다. 회원 토픽은 다른 서비스도 같이 받는 공용 토픽이라,
	// 기본 접미사(-retry, -dlt)를 쓰면 같은 토픽을 @RetryableTopic으로 받는 서비스끼리 재시도·DLT 토픽이 겹쳐 서로의 실패 메시지를 받는다
	// 재시도 토픽 이름은 접미사 뒤에 지연 시간이 붙는다 (예: queuing.member.joined.product.retry-1000)
	private static final String RETRY_TOPIC_SUFFIX = ".product.retry";
	// 마지막까지 실패한 메시지를 보낼 토픽 접미사 (예: queuing.member.joined.product.dlt)
	// ProductKafkaDltConsumer가 DLT 토픽 이름을 만들 때 같은 값을 쓴다
	static final String DLT_TOPIC_SUFFIX = ".product.dlt";
	// DLT는 ProductKafkaDltConsumer가 받으므로, @RetryableTopic이 기본으로 띄우는 DLT 리스너는 시작하지 않는다
	// (켜 두면 같은 DLT 메시지를 두 곳에서 받는다)
	private static final String AUTO_START_DEFAULT_DLT_HANDLER = "false";

	private final ProductFacade productFacade;
	private final JsonConverter jsonConverter;

	// 회원 서비스가 보내는 공용 토픽. 캐시 서비스도 groupId를 달리해 같은 메시지를 받는다
	@KafkaListener(topics = KafkaTopics.MEMBER_MEMBER_JOINED, groupId = GROUP_ID, concurrency = CONCURRENCY)
	@RetryableTopic(
		attempts = RETRY_ATTEMPTS,
		backOff = @BackOff(delay = RETRY_DELAY_MILLIS, multiplier = RETRY_MULTIPLIER),
		// 깨진 JSON, 모르는 Enum 값은 다시 해도 실패하므로 재시도 없이 바로 DLT로 보낸다
		// (JsonConverter가 파싱 실패를 IllegalArgumentException으로 바꾼다)
		exclude = IllegalArgumentException.class,
		numPartitions = PARTITIONS,
		retryTopicSuffix = RETRY_TOPIC_SUFFIX,
		dltTopicSuffix = DLT_TOPIC_SUFFIX,
		autoStartDltHandler = AUTO_START_DEFAULT_DLT_HANDLER
	)
	public void handleMemberJoined(String payload,
		@Header(name = KafkaMessageId.HEADER, required = false) String messageId) {
		MemberJoinedEvent event = jsonConverter.fromJson(payload, MemberJoinedEvent.class);
		productFacade.syncMember(messageId, KafkaTopics.MEMBER_MEMBER_JOINED,
			event.id(), event.createDate(), event.modifyDate(), event.nickname());
	}

	// 회원 서비스가 보내는 공용 토픽. 가입과 같은 방식으로 회원 복제본에 덮어쓴다
	@KafkaListener(topics = KafkaTopics.MEMBER_MODIFIED, groupId = GROUP_ID, concurrency = CONCURRENCY)
	@RetryableTopic(
		attempts = RETRY_ATTEMPTS,
		backOff = @BackOff(delay = RETRY_DELAY_MILLIS, multiplier = RETRY_MULTIPLIER),
		// 깨진 JSON은 다시 해도 실패하므로 재시도 없이 바로 DLT로 보낸다
		exclude = IllegalArgumentException.class,
		numPartitions = PARTITIONS,
		retryTopicSuffix = RETRY_TOPIC_SUFFIX,
		dltTopicSuffix = DLT_TOPIC_SUFFIX,
		autoStartDltHandler = AUTO_START_DEFAULT_DLT_HANDLER
	)
	public void handleMemberModified(String payload,
		@Header(name = KafkaMessageId.HEADER, required = false) String messageId) {
		MemberModifiedEvent event = jsonConverter.fromJson(payload, MemberModifiedEvent.class);
		productFacade.syncMember(messageId, KafkaTopics.MEMBER_MODIFIED,
			event.id(), event.createDate(), event.modifyDate(), event.nickname());
	}

	@KafkaListener(topics = KafkaTopics.SELLER_CHANGED, groupId = GROUP_ID, concurrency = CONCURRENCY)
	@RetryableTopic(
		attempts = RETRY_ATTEMPTS,
		backOff = @BackOff(delay = RETRY_DELAY_MILLIS, multiplier = RETRY_MULTIPLIER),
		// 깨진 JSON, 모르는 Enum 값은 다시 해도 실패하므로 재시도 없이 바로 DLT로 보낸다
		// (JsonConverter가 파싱 실패를 IllegalArgumentException으로 바꾼다)
		exclude = IllegalArgumentException.class,
		numPartitions = PARTITIONS,
		retryTopicSuffix = RETRY_TOPIC_SUFFIX,
		dltTopicSuffix = DLT_TOPIC_SUFFIX,
		autoStartDltHandler = AUTO_START_DEFAULT_DLT_HANDLER
	)
	public void handleSellerChanged(String payload,
		@Header(name = KafkaMessageId.HEADER, required = false) String messageId) {
		SellerChangedEvent event = jsonConverter.fromJson(payload, SellerChangedEvent.class);
		productFacade.syncSeller(messageId, KafkaTopics.SELLER_CHANGED, event.id(), event.memberId(),
			event.sellerType(), event.businessName(), event.businessNumber(), event.representativeName(),
			event.status(), event.requestedAt(), event.reviewedAt());
	}

	// 대기열 활성 시간이 끝나 결제하지 않은 자리를 해제한다 (주문 서비스가 보낸다)
	@KafkaListener(topics = KafkaTopics.PRODUCT_HOLD_CANCEL, groupId = GROUP_ID, concurrency = CONCURRENCY)
	@RetryableTopic(
		attempts = RETRY_ATTEMPTS,
		backOff = @BackOff(delay = RETRY_DELAY_MILLIS, multiplier = RETRY_MULTIPLIER),
		// 깨진 JSON은 다시 해도 실패하므로 재시도 없이 바로 DLT로 보낸다
		exclude = IllegalArgumentException.class,
		numPartitions = PARTITIONS,
		retryTopicSuffix = RETRY_TOPIC_SUFFIX,
		dltTopicSuffix = DLT_TOPIC_SUFFIX,
		autoStartDltHandler = AUTO_START_DEFAULT_DLT_HANDLER
	)
	public void handleHoldCancel(String payload,
		@Header(name = KafkaMessageId.HEADER, required = false) String messageId) {
		HoldCancelEvent event = jsonConverter.fromJson(payload, HoldCancelEvent.class);
		productFacade.cancelHold(messageId, KafkaTopics.PRODUCT_HOLD_CANCEL, event.participationId());
	}

	// 결제한 참가권을 취소·만료해 판매한 자리를 정원으로 되돌린다 (주문 서비스가 커밋 뒤 아웃박스로 보낸다)
	// 판매되지 않았거나 이미 되돌린 자리면 아무것도 하지 않으므로 같은 메시지를 두 번 받아도 된다
	@KafkaListener(topics = KafkaTopics.PRODUCT_HOLD_RESTORE, groupId = GROUP_ID, concurrency = CONCURRENCY)
	@RetryableTopic(
		attempts = RETRY_ATTEMPTS,
		backOff = @BackOff(delay = RETRY_DELAY_MILLIS, multiplier = RETRY_MULTIPLIER),
		// 깨진 JSON은 다시 해도 실패하므로 재시도 없이 바로 DLT로 보낸다
		exclude = IllegalArgumentException.class,
		numPartitions = PARTITIONS,
		retryTopicSuffix = RETRY_TOPIC_SUFFIX,
		dltTopicSuffix = DLT_TOPIC_SUFFIX,
		autoStartDltHandler = AUTO_START_DEFAULT_DLT_HANDLER
	)
	public void handleHoldRestore(String payload,
		@Header(name = KafkaMessageId.HEADER, required = false) String messageId) {
		HoldRestoreEvent event = jsonConverter.fromJson(payload, HoldRestoreEvent.class);
		productFacade.restoreSeat(messageId, KafkaTopics.PRODUCT_HOLD_RESTORE, event.participationId());
	}
}
