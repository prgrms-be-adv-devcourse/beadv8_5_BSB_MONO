package com.bukang.product.app;

/**
 * product가 받는 Kafka 메시지의 재시도·DLT 토픽 이름 규칙. 받는 쪽(ProductKafkaConsumer의 @RetryableTopic)과
 * 다시 보내는 쪽(ProductDltUseCase)이 같은 값을 쓰도록 한곳에 둔다. 값을 바꾸면 재시도·DLT 토픽 이름이 바뀐다.
 */
public final class ProductKafkaRetry {
	// 재시도·DLT 토픽 접미사에 서비스 이름을 넣는다. 회원 토픽은 다른 서비스도 같이 받는 공용 토픽이라,
	// 기본 접미사(-retry, -dlt)를 쓰면 같은 토픽을 @RetryableTopic으로 받는 서비스끼리 재시도·DLT 토픽이 겹쳐 서로의 실패 메시지를 받는다
	// 재시도 토픽 이름은 접미사 뒤에 지연 시간이 붙는다 (예: queuing.member.joined.product.retry-1000)
	public static final String RETRY_TOPIC_SUFFIX = ".product.retry";
	// 마지막까지 실패한 메시지를 보낼 토픽 접미사 (예: queuing.member.joined.product.dlt)
	public static final String DLT_TOPIC_SUFFIX = ".product.dlt";
	// 첫 재시도까지 기다리는 시간(ms). 첫 재시도 토픽 이름(...retry-1000)에도 들어간다
	public static final long FIRST_RETRY_DELAY_MILLIS = 1000L;

	private ProductKafkaRetry() {
	}
}
