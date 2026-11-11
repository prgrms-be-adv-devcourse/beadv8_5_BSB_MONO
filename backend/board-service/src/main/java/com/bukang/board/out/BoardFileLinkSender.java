package com.bukang.board.out;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.file.event.FileLinkRequestedEvent;
import com.bukang.common.shared.kafka.KafkaMessageId;
import com.bukang.common.shared.kafka.KafkaTopics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 글을 저장하는 트랜잭션이 커밋된 뒤에만 사진 연결 요청(FileLinkRequestedEvent)을 file-service로 보낸다.
 * 롤백된 글의 요청은 보내지 않는다. 보내지 못하면 로그만 남긴다 (글쓰기는 실패하지 않고 사진만 빠진다, 연결되지 않은 파일은
 * file-service 정리 배치가 하루 뒤 지운다). 예외를 던지지 않아 부른 쪽 응답에 영향이 없다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BoardFileLinkSender {
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final JsonConverter jsonConverter;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void send(FileLinkRequestedEvent event) {
		// 커밋 뒤 콜백에서 예외가 나면 부른 쪽이 커밋을 실패로 보므로, 메시지를 만드는 일까지 모두 잡는다
		// 브로커가 없으면 send()가 max.block.ms까지 멈추므로, 커밋을 마친 요청 쓰레드가 기다리지 않게 따로 보낸다
		// shortcut: 공용 ForkJoin 풀에서 보낸다. 글쓰기가 많아지고 Kafka 장애가 길어지면 전용 실행기로 바꾼다
		CompletableFuture.supplyAsync(() -> kafkaTemplate.send(record(event)))
			.thenCompose(sent -> sent)
			.exceptionally(failure -> {
				log.warn("사진 연결 요청을 보내지 못했습니다 (글은 저장됨). refs={}, cause={}", event.refs(), failure.toString());
				return null;
			});
	}

	// 같은 글의 요청은 같은 파티션으로 가도록 글 ID를 key로 쓴다. message-id는 받는 file-service 인박스가 중복을 거르는 값이다
	private ProducerRecord<String, String> record(FileLinkRequestedEvent event) {
		String key = String.valueOf(event.refs().getFirst().refId());
		return KafkaMessageId.record(KafkaTopics.RACE_FILE_LINK_REQUESTED, key, jsonConverter.toJson(event),
			"board-filelink-" + UUID.randomUUID());
	}
}
