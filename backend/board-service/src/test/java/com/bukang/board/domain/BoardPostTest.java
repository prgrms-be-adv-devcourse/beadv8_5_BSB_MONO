package com.bukang.board.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BoardPostTest {
	private BoardPost newPost() {
		return BoardPost.create(1L, BoardCategory.FREE, "제목", "본문");
	}

	@Test
	@DisplayName("새 글은 보이는 글이다")
	void newPostIsVisible() {
		assertThat(newPost().isVisible()).isTrue();
	}

	@Test
	@DisplayName("숨긴 글과 지운 글은 보이는 글이 아니다")
	void hiddenAndDeletedAreNotVisible() {
		BoardPost hidden = newPost();
		hidden.hide();
		BoardPost deleted = newPost();
		deleted.delete();

		assertThat(hidden.isVisible()).isFalse();
		assertThat(deleted.isVisible()).isFalse();
	}

	@Test
	@DisplayName("관리자가 되살리면 다시 보이는 글이다")
	void restoredIsVisible() {
		BoardPost post = newPost();
		post.hide();
		post.handle(ReportHandledAction.RESTORED, LocalDateTime.now());

		assertThat(post.isVisible()).isTrue();
	}
}
