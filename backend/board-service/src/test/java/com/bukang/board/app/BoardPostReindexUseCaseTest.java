package com.bukang.board.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

import com.bukang.board.domain.BoardCategory;
import com.bukang.board.domain.BoardPost;
import com.bukang.board.out.search.BoardPostSearchPort;
import com.bukang.board.support.BoardTestBase;

/**
 * 기존 글을 인덱스에 한 번 채운다 (BUK-179). 보이는 글만 묶음으로 넣고, 여러 번 돌려도 같은 결과다
 */
class BoardPostReindexUseCaseTest extends BoardTestBase {
	@Autowired
	private BoardPostReindexUseCase reindexUseCase;

	@SuppressWarnings("unchecked")
	private List<BoardPostSearchPort.Document> indexed(int batches) {
		ArgumentCaptor<List<BoardPostSearchPort.Document>> captor = ArgumentCaptor.forClass(List.class);
		verify(searchPort, times(batches)).indexAll(captor.capture());
		List<BoardPostSearchPort.Document> all = new ArrayList<>();
		captor.getAllValues().forEach(all::addAll);
		return all;
	}

	@Test
	@DisplayName("보이는 글만 넣고, 숨겨진 글과 지운 글은 넣지 않는다")
	void onlyVisible() throws Exception {
		long visible = newPost(ME, "보임");
		long hidden = newPost(ME, "숨김");
		hidePost(hidden);
		long deleted = newPost(ME, "삭제");
		deletePost(ME, deleted);

		assertThat(reindexUseCase.run()).isEqualTo(1);

		assertThat(indexed(1)).containsExactly(new BoardPostSearchPort.Document(visible, "보임", "본문"));
	}

	@Test
	@DisplayName("한 묶음(BATCH_SIZE)을 넘으면 나눠서 넣고, 빠지거나 겹치는 글이 없다")
	void batches() {
		int total = BoardPostReindexUseCase.BATCH_SIZE + 1;
		for (int i = 0; i < total; i++) {
			postRepository.save(BoardPost.create(ME, BoardCategory.FREE, "글" + i, "본문"));
		}

		assertThat(reindexUseCase.run()).isEqualTo(total);

		assertThat(indexed(2).stream().map(BoardPostSearchPort.Document::postId).distinct()).hasSize(total);
	}

	@Test
	@DisplayName("글이 없으면 아무것도 넣지 않는다")
	void empty() {
		assertThat(reindexUseCase.run()).isZero();

		verify(searchPort, times(0)).indexAll(anyList());
	}

	@Test
	@DisplayName("Elasticsearch가 멈춰 있으면 예외 없이 멈추고 지금까지 넣은 수를 돌려준다 (게시판은 정상으로 뜬다)")
	void searchDownStopsQuietly() throws Exception {
		newPost(ME, "글");
		doThrow(new IllegalStateException("es down")).when(searchPort).indexAll(anyList());

		assertThatCode(() -> assertThat(reindexUseCase.run()).isZero()).doesNotThrowAnyException();
	}
}
