package com.bukang.file.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.file.out.FileInboxKafkaEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 받은 Kafka 메시지를 인박스에 남겨, 같은 메시지를 두 번 처리하지 않게 한다.
 * MANDATORY: 메시지를 처리하는 트랜잭션 안에서만 부를 수 있다 (트랜잭션 밖에서 부르면 예외).
 * 처리와 함께 커밋·롤백돼야, 처리가 실패해 롤백된 메시지를 다시 받았을 때 "이미 처리함"으로 버리지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class FileInboxUseCase {
	private final FileInboxKafkaEventRepository fileInboxKafkaEventRepository;

	// 처리해야 하면 true, 이미 처리한 메시지면 false
	// messageId가 없으면(보낸 쪽이 message-id 헤더를 붙이지 않음) 거를 기준이 없으므로 인박스 없이 처리한다
	// 보낸 쪽이 헤더를 붙이기 시작하면 이 코드를 고치지 않아도 인박스로 거른다. 로그로 아직 헤더 없이 오는 토픽을 찾는다
	public boolean tryRecord(String messageId, String consumerGroup, String topic, LocalDateTime now) {
		if (messageId == null) {
			log.warn("message-id 헤더가 없는 메시지를 인박스 없이 처리합니다. group={}, topic={}", consumerGroup, topic);
			return true;
		}
		if (fileInboxKafkaEventRepository.insertIgnore(messageId, consumerGroup, topic, now) == 0) {
			log.info("이미 처리한 메시지를 건너뜁니다. messageId={}, group={}, topic={}", messageId, consumerGroup, topic);
			return false;
		}
		return true;
	}
}
