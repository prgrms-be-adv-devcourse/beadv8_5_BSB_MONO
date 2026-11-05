package com.bukang.file.app;

/**
 * file이 받는 Kafka 메시지의 재시도·DLT 토픽 이름 규칙. 받는 쪽(FileKafkaConsumer의 @RetryableTopic)과
 * 다시 보내는 쪽(FileDltUseCase)이 같은 값을 쓰도록 한곳에 둔다. 값을 바꾸면 재시도·DLT 토픽 이름이 바뀐다.
 */
public final class FileKafkaRetry {
	// 재시도 토픽 접미사. FileKafkaConsumer는 retryTopicSuffix를 적지 않아 Spring Kafka 기본값(-retry)을 쓴다
	// 파일 연결 토픽은 file만 받으므로 다른 서비스와 재시도 토픽이 겹치지 않는다. 이 값은 다시 보내기가 토픽 이름을 만들 때만 쓴다
	// 재시도 토픽 이름은 접미사 뒤에 지연 시간이 붙는다 (예: race.filelink.requested-retry-1000)
	public static final String RETRY_TOPIC_SUFFIX = "-retry";
	// 마지막까지 실패한 메시지를 보낼 토픽 접미사 (예: race.filelink.requested.dlt, 기본값은 -dlt)
	public static final String DLT_TOPIC_SUFFIX = ".dlt";
	// 첫 재시도까지 기다리는 시간(ms). 첫 재시도 토픽 이름(...retry-1000)에도 들어간다
	public static final long FIRST_RETRY_DELAY_MILLIS = 1000L;

	private FileKafkaRetry() {
	}
}
