package com.bukang.board.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bukang.board.domain.PostSort;
import com.bukang.board.domain.exception.BoardInvalidListRequestException;

class PostCursorTest {
	@Test
	@DisplayName("인기순 cursor는 '좋아요 수-글 ID' 형식이다")
	void formatsPopular() {
		assertThat(new PostCursor(7, 12).format(PostSort.POPULAR)).isEqualTo("7-12");
	}

	@Test
	@DisplayName("최신순 cursor는 글 ID만이다")
	void formatsLatest() {
		assertThat(new PostCursor(7, 12).format(PostSort.LATEST)).isEqualTo("12");
	}

	@Test
	@DisplayName("cursor가 없거나 비어 있으면 첫 쪽이라 모든 글이 뒤에 온다")
	void blankIsFirstPage() {
		PostCursor first = new PostCursor(Long.MAX_VALUE, Long.MAX_VALUE);

		assertThat(PostCursor.parse(PostSort.POPULAR, null)).isEqualTo(first);
		assertThat(PostCursor.parse(PostSort.POPULAR, " ")).isEqualTo(first);
		assertThat(PostCursor.parse(PostSort.LATEST, null).postId()).isEqualTo(Long.MAX_VALUE);
		assertThat(PostCursor.parse(PostSort.LATEST, "").postId()).isEqualTo(Long.MAX_VALUE);
	}

	@Test
	@DisplayName("인기순 cursor를 읽는다. 좋아요 수는 0도 된다")
	void parsesPopular() {
		assertThat(PostCursor.parse(PostSort.POPULAR, "7-12")).isEqualTo(new PostCursor(7, 12));
		assertThat(PostCursor.parse(PostSort.POPULAR, "0-5")).isEqualTo(new PostCursor(0, 5));
	}

	@Test
	@DisplayName("최신순 cursor를 읽는다")
	void parsesLatest() {
		assertThat(PostCursor.parse(PostSort.LATEST, "12").postId()).isEqualTo(12);
	}

	@Test
	@DisplayName("잘못된 인기순 cursor는 400 예외다")
	void invalidPopular() {
		for (String bad : new String[] {"7", "7-", "-5", "-1-5", "7--1", "a-b", "7-0", "7-12-3", "7-x"}) {
			assertThatThrownBy(() -> PostCursor.parse(PostSort.POPULAR, bad))
				.as(bad)
				.isInstanceOf(BoardInvalidListRequestException.class);
		}
	}

	@Test
	@DisplayName("잘못된 최신순 cursor는 400 예외다")
	void invalidLatest() {
		for (String bad : new String[] {"0", "-3", "abc", "1-2", "1.5"}) {
			assertThatThrownBy(() -> PostCursor.parse(PostSort.LATEST, bad))
				.as(bad)
				.isInstanceOf(BoardInvalidListRequestException.class);
		}
	}
}
