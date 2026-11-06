package com.bukang.file.in;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.bukang.common.shared.kafka.KafkaTopics;
import com.bukang.common.shared.kafka.dlt.KafkaDltMessage;
import com.bukang.file.app.FileFacade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 재시도를 모두 실패했거나 재시도할 필요가 없는 실패로 DLT(<토픽>.dlt)에 간 메시지를 받아
 * 실패 테이블(FILE_DLT_KAFKA_EVENT)에 남기고 Slack으로 알린다.
 * 자동으로 다시 처리하지 않는다. 원인을 고친 뒤 내부 API(/internal/v1/file/kafka-failures/{id}/resend)로 다시 보낸다.
 * 헤더(message-id, 예외 정보)를 읽어야 하므로 본문만이 아니라 ConsumerRecord로 받는다.
 * 테이블에 남기다 예외가 나면 기본 오류 처리기가 같은 메시지를 다시 시도하고, 끝내 실패하면 에러 로그를 남기고 건너뛴다
 * (이때도 메시지는 DLT 토픽과 아래 에러 로그에 남는다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileKafkaDltConsumer {
	// 본 리스너(file)와 오프셋을 따로 관리한다
	private static final String GROUP_ID = "file-dlt";
	private static final String FILE_LINK_REQUESTED_DLT = KafkaTopics.RACE_FILE_LINK_REQUESTED
		+ FileKafkaConsumer.DLT_TOPIC_SUFFIX;

	private final FileFacade fileFacade;

	// 연결되지 않은 파일은 하루 뒤 정리 배치가 지우므로, 다시 보내려면 그 전에 보내야 한다
	@KafkaListener(topics = FILE_LINK_REQUESTED_DLT, groupId = GROUP_ID)
	public void handleFileLinkRequestedDlt(ConsumerRecord<String, String> record) {
		log.error("처리하지 못한 파일 연결 메시지: {}", record.value());
		record(record);
	}

	// DLT 레코드에서 원래 토픽·DLT 위치·message-id·예외를 꺼내 실패 테이블에 남긴다
	private void record(ConsumerRecord<String, String> record) {
		fileFacade.recordDlt(KafkaDltMessage.from(record, FileKafkaConsumer.DLT_TOPIC_SUFFIX));
	}
}
