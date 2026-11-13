package com.bukang.board.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.board.out.BoardInboxKafkaEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 받은 Kafka 메시지를 인박스에 남겨, 같은 메시지를 두 번 처리하지 않게 한다 (상품 서비스 ProductInboxUseCase와 같은 방식).
 * MANDATORY: 메시지를 처리하는 트랜잭션 안에서만 부를 수 있다 (트랜잭션 밖에서 부르면 예외).
 * 처리와 함께 커밋·롤백돼야, 처리가 실패해 롤백된 메시지를 다시 받았을 때 "이미 처리함"으로 버리지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class BoardInboxUseCase {
	// 인박스에 남기는 컨슈머 그룹. BoardKafkaListener의 groupId와 같은 값이다
	public static final String CONSUMER_GROUP = "board";

	private final BoardInboxKafkaEventRepository boardInboxKafkaEventRepository;

	// 처리해야 하면 true, 이미 처리한 메시지면 false
	// messageId가 없으면(보낸 쪽이 message-id 헤더를 붙이지 않음) 거를 기준이 없으므로 인박스 없이 처리한다
	public boolean tryRecord(String messageId, String topic, LocalDateTime now) {
		if (messageId == null) {
			log.warn("message-id 헤더가 없는 메시지를 인박스 없이 처리합니다. topic={}", topic);
			return true;
		}
		if (boardInboxKafkaEventRepository.insertIgnore(messageId, CONSUMER_GROUP, topic, now) == 0) {
			log.info("이미 처리한 메시지를 건너뜁니다. messageId={}, topic={}", messageId, topic);
			return false;
		}
		return true;
	}
}
