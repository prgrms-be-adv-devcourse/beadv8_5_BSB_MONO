package com.bukang.common.event;

/**
 * 서비스 간에 주고받는 Kafka 토픽 이름 (생산자와 소비자가 같은 상수를 참조한다)
 * 이름은 .claude/rules/kafka-topic-convention.md 의 <message-type>.<dataset-name>.<data-name> 형식을 따른다.
 */
public final class KafkaTopics {
	public static final String MEMBER_JOINED = "queuing.member.joined";

	private KafkaTopics() {
	}
}
