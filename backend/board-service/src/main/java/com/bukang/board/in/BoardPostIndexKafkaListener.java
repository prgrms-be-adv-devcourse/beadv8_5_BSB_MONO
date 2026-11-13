package com.bukang.board.in;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.bukang.board.app.BoardInboxUseCase;
import com.bukang.board.app.BoardPostIndexUseCase;
import com.bukang.board.config.BoardKafkaConfig;
import com.bukang.board.domain.BoardPostChangedEvent;
import com.bukang.common.global.json.JsonConverter;
import com.bukang.common.shared.kafka.KafkaMessageId;
import com.bukang.common.shared.kafka.KafkaTopics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 글이 바뀌었다는 메시지(queuing.post.changed)를 받아 검색 인덱스에 반영한다 (BUK-179). 게시판이 보내고 게시판이 받는다.
 * 읽을 수 없는 메시지(깨진 JSON)는 다시 받아도 같은 결과라 로그만 남기고 넘어간다.
 * Elasticsearch 오류는 던져서 컨테이너가 간격을 늘려 다시 시도하게 한다 (BoardKafkaConfig, 끝내 안 되면 다음 기동 때 채운다)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BoardPostIndexKafkaListener {
	// groupId는 바꾸지 않는다. 바꾸면 새 그룹으로 보고 토픽을 처음부터 다시 읽는다 (auto-offset-reset: earliest)
	private static final String GROUP_ID = BoardInboxUseCase.CONSUMER_GROUP;

	private final JsonConverter jsonConverter;
	private final BoardPostIndexUseCase boardPostIndexUseCase;

	// 같은 글의 메시지는 글 ID key라 같은 파티션으로 순서대로 들어오므로 쓰레드 하나로 처리한다
	@KafkaListener(topics = KafkaTopics.BOARD_POST_CHANGED, groupId = GROUP_ID, concurrency = "1",
		containerFactory = BoardKafkaConfig.SEARCH_LISTENER_FACTORY)
	public void handlePostChanged(String payload,
		@Header(name = KafkaMessageId.HEADER, required = false) String messageId) {
		BoardPostChangedEvent event;
		try {
			event = jsonConverter.fromJson(payload, BoardPostChangedEvent.class);
		} catch (IllegalArgumentException broken) {
			log.warn("읽을 수 없는 메시지라 버립니다. payload={}", payload, broken);
			return;
		}
		boardPostIndexUseCase.apply(messageId, event.postId());
	}
}
