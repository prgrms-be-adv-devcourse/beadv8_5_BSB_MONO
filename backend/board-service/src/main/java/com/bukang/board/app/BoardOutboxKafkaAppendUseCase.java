package com.bukang.board.app;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.board.domain.BoardOutboxKafkaEvent;
import com.bukang.board.domain.BoardPost;
import com.bukang.board.domain.BoardPostChangedEvent;
import com.bukang.board.out.BoardOutboxKafkaEventRepository;
import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.KafkaTopics;

import lombok.RequiredArgsConstructor;

/**
 * 보낼 Kafka 메시지를 아웃박스 테이블에 저장한다. 보내는 일은 BoardOutboxKafkaRelayUseCase가 한다.
 * MANDATORY: 글을 저장하는 트랜잭션 안에서만 부를 수 있다 (트랜잭션 밖에서 부르면 예외).
 * 따로 커밋하면 글 저장이 롤백돼도 메시지가 남거나, 메시지 저장만 실패할 수 있기 때문이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class BoardOutboxKafkaAppendUseCase {
	private static final String POST_REF_TYPE = BoardPost.class.getSimpleName();

	private final BoardOutboxKafkaEventRepository boardOutboxKafkaEventRepository;
	private final JsonConverter jsonConverter;

	// 글이 바뀌었다고 남긴다 (쓰기·고치기·지우기·숨김·되살리기). 글 ID를 key로 보낸다
	public void appendPostChanged(long postId) {
		boardOutboxKafkaEventRepository.save(BoardOutboxKafkaEvent.pending(POST_REF_TYPE, postId,
			KafkaTopics.BOARD_POST_CHANGED, jsonConverter.toJson(new BoardPostChangedEvent(postId))));
	}
}
