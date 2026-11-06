package com.bukang.queue.app;

/**
 * queue가 받는 Kafka 메시지의 재시도·DLT 토픽 이름 규칙. 받는 쪽(QueueKafkaConsumer의 @RetryableTopic)과
 * 다시 보내는 쪽(QueueDltUseCase)이 같은 값을 쓰도록 한곳에 둔다. 값을 바꾸면 재시도·DLT 토픽 이름이 바뀐다.
 */
public final class QueueKafkaRetry {
	// 재시도·DLT 토픽 접미사에 서비스 이름을 넣는다 (KafkaTopics 규칙)
	// 다른 서비스도 대회·종목 토픽을 @RetryableTopic으로 받게 되면 기본 접미사(-retry, -dlt)로는 재시도·DLT 토픽이 겹친다
	// 재시도 토픽 이름은 접미사 뒤에 지연 시간이 붙는다 (예: queuing.race.changed.queue.retry-1000)
	public static final String RETRY_TOPIC_SUFFIX = ".queue.retry";
	// 마지막까지 실패한 메시지를 보낼 토픽 접미사 (예: queuing.race.changed.queue.dlt)
	public static final String DLT_TOPIC_SUFFIX = ".queue.dlt";
	// 첫 재시도까지 기다리는 시간(ms). 첫 재시도 토픽 이름(...retry-1000)에도 들어간다
	public static final long FIRST_RETRY_DELAY_MILLIS = 1000L;

	private QueueKafkaRetry() {
	}
}
