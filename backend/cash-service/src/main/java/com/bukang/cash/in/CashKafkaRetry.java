package com.bukang.cash.in;

import com.bukang.common.shared.kafka.KafkaTopics;

/**
 * cash 리스너의 @RetryableTopic 설정값과 DLT 토픽 이름 (BUK-12)
 * CashEventListener(재시도)와 CashEventDltListener(DLT 구독)가 같은 값을 쓰도록 한곳에 모은다.
 * 애노테이션 속성에 넣는 값이라 컴파일 시점 상수로만 둔다.
 */
final class CashKafkaRetry {
	// 처음 시도를 포함한 시도 횟수. 실패하면 1초 → 2초 간격(2배수)으로 2번 다시 시도한다
	// 줄이면 마지막 재시도 토픽을 읽는 리스너가 없어지므로, 재시도 토픽이 비어 있을 때 바꾼다
	static final String ATTEMPTS = "3";
	static final long DELAY_MILLIS = 1000L;
	static final double MULTIPLIER = 2.0;
	// 재시도·DLT 토픽 파티션 수. 원래 토픽(브로커 기본값 1개)과 맞춘다
	static final String PARTITIONS = "1";
	// 회원·판매자 토픽은 product도 받는 공용 토픽이라, 접미사에 서비스 이름을 넣어 재시도·DLT 토픽이 겹치지 않게 한다
	// 리스너는 topicSuffixingStrategy = SUFFIX_WITH_INDEX_VALUE로 접미사 뒤에 순번(-0, -1)을 붙인다
	// (기본값처럼 기다리는 시간을 붙이면 간격을 바꿔 배포할 때 토픽 이름이 바뀌어, 옛 재시도 토픽에 남은 메시지를 아무도 읽지 않는다)
	static final String RETRY_TOPIC_SUFFIX = ".cash.retry";
	static final String DLT_TOPIC_SUFFIX = ".cash.dlt";
	// DLT는 CashEventDltListener가 받으므로, @RetryableTopic이 기본으로 띄우는 DLT 리스너는 시작하지 않는다
	// (켜 두면 같은 DLT 메시지를 두 곳에서 받는다)
	static final String AUTO_START_DEFAULT_DLT_HANDLER = "false";

	static final String MEMBER_JOINED_DLT = KafkaTopics.MEMBER_MEMBER_JOINED + DLT_TOPIC_SUFFIX;
	static final String MEMBER_MODIFIED_DLT = KafkaTopics.MEMBER_MODIFIED + DLT_TOPIC_SUFFIX;
	static final String SELLER_CHANGED_DLT = KafkaTopics.SELLER_CHANGED + DLT_TOPIC_SUFFIX;

	private CashKafkaRetry() {
	}
}
