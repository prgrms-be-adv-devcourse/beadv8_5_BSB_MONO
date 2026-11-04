package com.bukang.cash.in;

import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.stereotype.Component;

import com.bukang.cash.app.CashFacade;
import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.member.event.MemberJoinedEvent;
import com.bukang.common.shared.member.event.MemberModifiedEvent;
import com.bukang.common.shared.seller.event.SellerChangedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CashEventListener {
	private final JsonConverter jsonConverter;
	private final CashFacade cashFacade;

	// groupId는 바꾸지 않는다. 바꾸면 새 그룹으로 보고 토픽을 처음부터 다시 읽는다 (auto-offset-reset: earliest)
	@KafkaListener(topics = KafkaTopics.MEMBER_MEMBER_JOINED, groupId = "CashEventListener__handleMemberJoined__1")
	@RetryableTopic(
		attempts = CashKafkaRetry.ATTEMPTS,
		backOff = @BackOff(delay = CashKafkaRetry.DELAY_MILLIS, multiplier = CashKafkaRetry.MULTIPLIER),
		// 깨진 JSON은 다시 해도 실패하므로 재시도 없이 바로 DLT로 보낸다
		// (JsonConverter가 파싱 실패를 IllegalArgumentException으로 바꾼다)
		exclude = IllegalArgumentException.class,
		numPartitions = CashKafkaRetry.PARTITIONS,
		retryTopicSuffix = CashKafkaRetry.RETRY_TOPIC_SUFFIX,
		dltTopicSuffix = CashKafkaRetry.DLT_TOPIC_SUFFIX,
		topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
		autoStartDltHandler = CashKafkaRetry.AUTO_START_DEFAULT_DLT_HANDLER
	)
	public void handleMemberJoined(String message) {
		MemberJoinedEvent event = jsonConverter.fromJson(message, MemberJoinedEvent.class);
		cashFacade.syncMember(event.id(), event.createDate(), event.modifyDate(), event.nickname());
	}

	@KafkaListener(topics = KafkaTopics.MEMBER_MODIFIED, groupId = "CashEventListener__handleMemberModified__1")
	@RetryableTopic(
		attempts = CashKafkaRetry.ATTEMPTS,
		backOff = @BackOff(delay = CashKafkaRetry.DELAY_MILLIS, multiplier = CashKafkaRetry.MULTIPLIER),
		// 깨진 JSON은 다시 해도 실패하므로 재시도 없이 바로 DLT로 보낸다
		exclude = IllegalArgumentException.class,
		numPartitions = CashKafkaRetry.PARTITIONS,
		retryTopicSuffix = CashKafkaRetry.RETRY_TOPIC_SUFFIX,
		dltTopicSuffix = CashKafkaRetry.DLT_TOPIC_SUFFIX,
		topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
		autoStartDltHandler = CashKafkaRetry.AUTO_START_DEFAULT_DLT_HANDLER
	)
	public void handleMemberModified(String message) {
		MemberModifiedEvent event = jsonConverter.fromJson(message, MemberModifiedEvent.class);
		cashFacade.syncMember(event.id(), event.createDate(), event.modifyDate(), event.nickname());
	}

	// 판매자 등록 신청·승인·반려 때마다 온다. 정산 지급 API가 받는 sellerId(신청서 ID)를 회원 ID로 바꿀 때 쓴다 (PRO-256)
	@KafkaListener(topics = KafkaTopics.SELLER_CHANGED, groupId = "CashEventListener__handleSellerChanged__1")
	@RetryableTopic(
		attempts = CashKafkaRetry.ATTEMPTS,
		backOff = @BackOff(delay = CashKafkaRetry.DELAY_MILLIS, multiplier = CashKafkaRetry.MULTIPLIER),
		// 깨진 JSON, 모르는 Enum 값은 다시 해도 실패하므로 재시도 없이 바로 DLT로 보낸다
		exclude = IllegalArgumentException.class,
		numPartitions = CashKafkaRetry.PARTITIONS,
		retryTopicSuffix = CashKafkaRetry.RETRY_TOPIC_SUFFIX,
		dltTopicSuffix = CashKafkaRetry.DLT_TOPIC_SUFFIX,
		topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
		autoStartDltHandler = CashKafkaRetry.AUTO_START_DEFAULT_DLT_HANDLER
	)
	public void handleSellerChanged(String message) {
		SellerChangedEvent event = jsonConverter.fromJson(message, SellerChangedEvent.class);
		cashFacade.syncSeller(event);
	}
}
