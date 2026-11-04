package com.bukang.file.in;

import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.bukang.common.global.exception.BusinessException;
import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.file.event.FileLinkRequestedEvent;
import com.bukang.common.shared.kafka.KafkaMessageId;
import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.file.app.FileFacade;
import com.bukang.file.app.FileKafkaRetry;

import lombok.RequiredArgsConstructor;

/**
 * 대상 서비스(product)가 대회·종목을 저장한 뒤 보낸 파일 연결 요청을 받아 파일을 연결한다
 * 메시지는 JSON 문자열로 받아 JsonConverter로 바꾼다. 연결 규칙은 동기 연결 API(InternalV1FileController)와 같다.
 * 처리 중 예외가 나면 재시도 토픽으로 보내 간격을 늘려 가며 다시 시도하고, 그래도 실패하면 <토픽>.dlt로 보낸다.
 * DLT 메시지는 FileKafkaDltConsumer가 받는다.
 * 상품이 붙인 message-id 헤더(product-outbox-{행 id})로 인박스가 같은 메시지를 두 번 처리하지 않는다
 * (재시도·DLT 토픽으로 넘어가도 헤더는 그대로다).
 */
@Component
@RequiredArgsConstructor
public class FileKafkaConsumer {
	// application.yaml에 group-id가 없으므로 리스너마다 적는다. 인박스에 남기는 그룹(FileFacade.KAFKA_GROUP_ID)과 같은 값이다
	private static final String GROUP_ID = "file";
	// 토픽 파티션 수 (KafkaTopicConfig.PARTITIONS와 맞춘다). 재시도·DLT 토픽도 같은 수로 만든다 (기본값은 1개)
	private static final String PARTITIONS = "1";
	// 동시에 처리할 쓰레드 수 (재시도·DLT 리스너도 같은 수). 파티션 수까지만 효과가 있다
	private static final String CONCURRENCY = "1";
	// 처음 시도를 포함한 시도 횟수. 실패하면 1초 → 2초 간격(2배수)으로 2번 다시 시도한다
	private static final String RETRY_ATTEMPTS = "3";
	// 첫 재시도 지연과 재시도·DLT 토픽 접미사는 FileKafkaRetry에 있다. 다시 보내기(FileDltUseCase)가 같은 값으로
	// 첫 재시도 토픽 이름(예: race.filelink.requested-retry-1000)을 만든다
	// 재시도 토픽 접미사는 적지 않아 Spring Kafka 기본값(-retry, FileKafkaRetry.RETRY_TOPIC_SUFFIX와 같은 값)을 쓴다
	private static final long RETRY_DELAY_MILLIS = FileKafkaRetry.FIRST_RETRY_DELAY_MILLIS;
	private static final double RETRY_MULTIPLIER = 2.0;
	// FileKafkaDltConsumer가 DLT 토픽 이름을 만들 때 같은 값을 쓴다
	static final String DLT_TOPIC_SUFFIX = FileKafkaRetry.DLT_TOPIC_SUFFIX;
	// DLT는 FileKafkaDltConsumer가 받으므로, @RetryableTopic이 기본으로 띄우는 DLT 리스너는 시작하지 않는다
	private static final String AUTO_START_DEFAULT_DLT_HANDLER = "false";

	private final FileFacade fileFacade;
	private final JsonConverter jsonConverter;

	@KafkaListener(topics = KafkaTopics.RACE_FILE_LINK_REQUESTED, groupId = GROUP_ID, concurrency = CONCURRENCY)
	@RetryableTopic(
		attempts = RETRY_ATTEMPTS,
		backOff = @BackOff(delay = RETRY_DELAY_MILLIS, multiplier = RETRY_MULTIPLIER),
		// 다시 해도 같은 결과인 실패는 재시도 없이 바로 DLT로 보낸다
		//   IllegalArgumentException: 깨진 JSON, 모르는 Enum 값 (JsonConverter가 파싱 실패를 바꾼 예외)
		//   BusinessException: 없는 파일, 남의 파일, 개수 초과, 다른 대상에 연결된 파일 같은 연결 거절
		exclude = {IllegalArgumentException.class, BusinessException.class},
		numPartitions = PARTITIONS,
		dltTopicSuffix = DLT_TOPIC_SUFFIX,
		autoStartDltHandler = AUTO_START_DEFAULT_DLT_HANDLER
	)
	public void handleFileLinkRequested(String payload,
		@Header(name = KafkaMessageId.HEADER, required = false) String messageId
	) {
		FileLinkRequestedEvent event = jsonConverter.fromJson(payload, FileLinkRequestedEvent.class);
		fileFacade.linkAll(messageId, KafkaTopics.RACE_FILE_LINK_REQUESTED, event.ownerId(), event.refs());
	}
}
