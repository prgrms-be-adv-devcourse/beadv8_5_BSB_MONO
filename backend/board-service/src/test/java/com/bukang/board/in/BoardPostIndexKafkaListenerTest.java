package com.bukang.board.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;

import com.bukang.board.out.search.BoardPostSearchPort;
import com.bukang.board.support.BoardTestBase;
import com.bukang.common.shared.kafka.KafkaTopics;

/**
 * 색인 토픽 메시지를 받아 인덱스에 반영하는 리스너: 토픽 계약, 메시지 전달, 읽을 수 없는 메시지 처리
 */
class BoardPostIndexKafkaListenerTest extends BoardTestBase {
	@Autowired
	private BoardPostIndexKafkaListener listener;

	@Test
	@DisplayName("색인 토픽 이름은 팀 규칙(<유형>.<묶음>.<데이터>, 소문자 한 단어씩)에 맞고, 리스너가 그 토픽을 받는다")
	void topicContract() {
		assertThat(KafkaTopics.BOARD_POST_CHANGED).isEqualTo("queuing.post.changed");
		assertThat(KafkaTopics.BOARD_POST_CHANGED).matches("^[a-z]+(\\.[a-z]+){2}$");
		assertThat(Arrays.stream(BoardPostIndexKafkaListener.class.getDeclaredMethods())
			.map(method -> method.getAnnotation(KafkaListener.class))
			.filter(java.util.Objects::nonNull)
			.flatMap(annotation -> Arrays.stream(annotation.topics()))).containsExactly(KafkaTopics.BOARD_POST_CHANGED);
	}

	@Test
	@DisplayName("메시지의 글 ID로 DB의 글을 읽어 인덱스에 넣는다")
	void indexesFromMessage() throws Exception {
		long postId = newPost(ME, "한강 러닝");

		listener.handlePostChanged("{\"postId\":" + postId + "}", "board-outbox-1");

		verify(searchPort).index(new BoardPostSearchPort.Document(postId, "한강 러닝", "본문"));
	}

	@Test
	@DisplayName("읽을 수 없는 메시지는 다시 받아도 같은 결과라 로그만 남기고 넘어간다")
	void brokenPayloadDropped() {
		assertThatCode(() -> listener.handlePostChanged("이건 JSON이 아님", "board-outbox-1")).doesNotThrowAnyException();

		verify(searchPort, never()).index(any());
		verify(searchPort, never()).delete(org.mockito.ArgumentMatchers.anyLong());
	}
}
