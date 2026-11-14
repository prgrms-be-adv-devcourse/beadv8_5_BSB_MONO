package com.bukang.order.app;

/**
 * order가 받는 Kafka 메시지의 재시도·DLT 토픽 이름 규칙 (상품 ProductKafkaRetry와 같은 방식).
 * 받는 쪽(OrderKafkaConsumer의 @RetryableTopic)과 다시 보내는 쪽(OrderDltUseCase)이 같은 값을 쓰도록 한곳에 둔다.
 * 값을 바꾸면 재시도·DLT 토픽 이름이 바뀐다.
 */
public final class OrderKafkaRetry {
	// 재시도·DLT 토픽 이름에 서비스 이름을 넣어 같은 토픽을 받는 다른 서비스의 재시도 토픽과 겹치지 않게 한다
	// 재시도 토픽 이름은 접미사 뒤에 지연 시간이 붙는다 (예: order.race.cancel.order.retry-10000)
	public static final String RETRY_TOPIC_SUFFIX = ".order.retry";
	// 마지막까지 실패한 메시지를 보낼 토픽 접미사 (예: order.race.cancel.order.dlt)
	public static final String DLT_TOPIC_SUFFIX = ".order.dlt";
	// 첫 재시도까지 기다리는 시간(ms). 첫 재시도 토픽 이름(...retry-10000)에도 들어간다
	public static final long FIRST_RETRY_DELAY_MILLIS = 10_000L;

	private OrderKafkaRetry() {
	}
}
