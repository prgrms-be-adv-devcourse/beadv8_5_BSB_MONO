package com.bukang.board.app;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.board.domain.BoardPostStatus;
import com.bukang.board.out.BoardPostRepository;
import com.bukang.board.out.search.BoardPostSearchPort;
import com.bukang.common.shared.kafka.KafkaTopics;

import lombok.RequiredArgsConstructor;

/**
 * 글이 바뀌었다는 메시지를 받아 검색 인덱스를 DB의 글 상태에 맞춘다 (BUK-179)
 * 메시지에는 글 ID만 있고, 보이는 글이면 DB의 지금 제목·본문으로 넣고 아니면(숨김·삭제·없음) 지운다.
 * 그래서 같은 메시지가 두 번 와도, 수정·삭제·숨김 메시지의 순서가 뒤바뀌어도 결과가 같다.
 * 인박스 기록과 같은 트랜잭션이라, Elasticsearch가 실패하면 기록도 롤백되어 같은 메시지를 다시 받았을 때 다시 시도한다
 */
@Service
@RequiredArgsConstructor
public class BoardPostIndexUseCase {
	private final BoardInboxUseCase boardInboxUseCase;
	private final BoardPostRepository boardPostRepository;
	private final BoardPostSearchPort boardPostSearchPort;

	@Transactional
	public void apply(String messageId, long postId) {
		if (!boardInboxUseCase.tryRecord(messageId, KafkaTopics.BOARD_POST_CHANGED, LocalDateTime.now())) {
			return;
		}
		boardPostRepository.findById(postId)
			.filter(post -> post.getStatus() == BoardPostStatus.VISIBLE)
			.ifPresentOrElse(
				post -> boardPostSearchPort.index(
					new BoardPostSearchPort.Document(post.getId(), post.getTitle(), post.getContent())),
				() -> boardPostSearchPort.delete(postId));
	}
}
