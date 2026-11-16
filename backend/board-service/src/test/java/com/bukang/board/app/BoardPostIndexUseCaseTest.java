package com.bukang.board.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.bukang.board.out.search.BoardPostSearchPort;
import com.bukang.board.support.BoardTestBase;

/**
 * 색인 소비자가 하는 일. 메시지에는 글 ID만 있고, 넣을지 지울지는 처리하는 순간의 DB 상태로 정한다.
 * 그래서 메시지가 두 번 오거나 순서가 뒤바뀌어도 결과가 같다 (수정·삭제·숨김이 겹쳐도 숨긴 글이 검색에 살아나지 않는다)
 */
class BoardPostIndexUseCaseTest extends BoardTestBase {
	@Autowired
	private BoardPostIndexUseCase indexUseCase;

	@Test
	@DisplayName("보이는 글은 DB의 지금 제목·본문으로 인덱스에 넣는다")
	void indexesVisiblePost() throws Exception {
		long postId = newPost(ME, "한강 러닝");

		indexUseCase.apply("m-1", postId);

		verify(searchPort).index(new BoardPostSearchPort.Document(postId, "한강 러닝", "본문"));
		verify(searchPort, never()).delete(postId);
	}

	@Test
	@DisplayName("숨겨진 글·지운 글·없는 글은 인덱스에서 지운다")
	void deletesNotVisible() throws Exception {
		long hidden = newPost(ME, "숨김");
		hidePost(hidden);
		long deleted = newPost(ME, "삭제");
		deletePost(ME, deleted);

		indexUseCase.apply("m-1", hidden);
		indexUseCase.apply("m-2", deleted);
		indexUseCase.apply("m-3", 987654L);

		verify(searchPort).delete(hidden);
		verify(searchPort).delete(deleted);
		verify(searchPort).delete(987654L);
		verify(searchPort, never()).index(any());
	}

	@Test
	@DisplayName("같은 메시지(message-id)가 두 번 와도 한 번만 반영한다")
	void sameMessageOnce() throws Exception {
		long postId = newPost(ME, "한 번만");

		indexUseCase.apply("m-1", postId);
		indexUseCase.apply("m-1", postId);

		verify(searchPort, times(1)).index(any());
	}

	@Test
	@DisplayName("수정 메시지가 삭제 메시지보다 늦게 와도, 지금 DB에서 지워진 글은 되살아나지 않는다")
	void lateEditAfterDeleteDoesNotResurrect() throws Exception {
		long postId = newPost(ME, "곧 지움");
		putPost(ME, postId, postBody("고침", "본문")).andReturn();
		deletePost(ME, postId).andReturn();

		// 삭제 메시지가 먼저, 수정 메시지가 나중에 도착한다
		indexUseCase.apply("m-delete", postId);
		indexUseCase.apply("m-edit", postId);

		verify(searchPort, never()).index(any());
		verify(searchPort, times(2)).delete(postId);
	}

	@Test
	@DisplayName("숨김→되살리기가 연달아 일어나 메시지가 늦게 와도, 처리하는 순간 DB가 보이는 글이면 인덱스에 넣는다")
	void followsCurrentDbStateAcrossHideAndRestore() throws Exception {
		long postId = newPost(ME, "되살아난 글");
		hidePost(postId);
		jdbcTemplate.update("update board_post set status = 'VISIBLE' where id = ?", postId);

		indexUseCase.apply("m-hide", postId);
		indexUseCase.apply("m-restore", postId);

		verify(searchPort, times(2)).index(new BoardPostSearchPort.Document(postId, "되살아난 글", "본문"));
		verify(searchPort, never()).delete(postId);

		hidePost(postId);
		indexUseCase.apply("m-hide-again", postId);
		verify(searchPort).delete(postId);
	}

	@Test
	@DisplayName("Elasticsearch가 실패하면 예외를 던지고 처리 기록을 남기지 않아, 같은 메시지를 다시 받으면 다시 시도한다")
	void failureKeepsMessageRetryable() throws Exception {
		long postId = newPost(ME, "재시도");
		doThrow(new IllegalStateException("es down")).when(searchPort).index(any());

		assertThatThrownBy(() -> indexUseCase.apply("m-1", postId)).isInstanceOf(IllegalStateException.class);
		assertThat(inboxRepository.count()).isZero();

		doNothing().when(searchPort).index(any());
		indexUseCase.apply("m-1", postId);

		assertThat(inboxRepository.count()).isEqualTo(1);
		verify(searchPort, times(2)).index(any());
	}

	@Test
	@DisplayName("message-id 헤더가 없는 메시지는 거를 기준이 없어 그대로 처리한다")
	void withoutMessageId() throws Exception {
		long postId = newPost(ME, "헤더 없음");

		indexUseCase.apply(null, postId);
		indexUseCase.apply(null, postId);

		verify(searchPort, times(2)).index(any());
	}
}
