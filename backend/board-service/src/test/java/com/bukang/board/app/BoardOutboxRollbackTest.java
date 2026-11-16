package com.bukang.board.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.bukang.board.support.BoardTestBase;

/**
 * 아웃박스 행을 남기지 못하면 글 저장도 함께 취소된다 (같은 트랜잭션). 저장은 됐는데 색인 메시지만 빠지는 일이 없다
 */
class BoardOutboxRollbackTest extends BoardTestBase {
	@MockitoBean
	private BoardOutboxKafkaAppendUseCase appendUseCase;

	@Test
	@DisplayName("아웃박스 저장이 실패하면 새 글도 남지 않는다")
	void postRollsBackWithOutbox() throws Exception {
		doThrow(new IllegalStateException("outbox down")).when(appendUseCase).appendPostChanged(anyLong());

		createPost(ME, postBody("제목", "본문")).andExpect(status().is5xxServerError());

		assertThat(postRepository.count()).isZero();
	}
}
