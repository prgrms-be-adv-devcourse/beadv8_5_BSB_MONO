package com.bukang.board.app;

import com.bukang.board.domain.BoardPost;
import com.bukang.board.domain.PostSort;
import com.bukang.board.domain.exception.BoardInvalidListRequestException;

/**
 * 목록 cursor. 앞 쪽 마지막 글의 값이다. 최신순은 글 ID, 인기순은 "좋아요 수-글 ID"
 * 처음 쪽(cursor가 없거나 비어 있음)은 최댓값이라 모든 글이 뒤에 온다. 최신순은 글 ID만 쓴다
 */
record PostCursor(long likeCount, long postId) {
	private static final PostCursor FIRST_PAGE = new PostCursor(Long.MAX_VALUE, Long.MAX_VALUE);

	static PostCursor of(BoardPost last) {
		return new PostCursor(last.getLikeCount(), last.getId());
	}

	static PostCursor parse(PostSort sort, String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return FIRST_PAGE;
		}
		if (sort != PostSort.POPULAR) {
			return new PostCursor(Long.MAX_VALUE, parsePositive(cursor));
		}
		String[] parts = cursor.split("-", -1);
		if (parts.length != 2) {
			throw invalid();
		}
		return new PostCursor(parseNumber(parts[0]), parsePositive(parts[1]));
	}

	String format(PostSort sort) {
		return sort == PostSort.POPULAR ? likeCount + "-" + postId : String.valueOf(postId);
	}

	private static long parsePositive(String text) {
		long value = parseNumber(text);
		if (value < 1) {
			throw invalid();
		}
		return value;
	}

	private static long parseNumber(String text) {
		try {
			long value = Long.parseLong(text);
			if (value < 0) {
				throw invalid();
			}
			return value;
		} catch (NumberFormatException exception) {
			throw invalid();
		}
	}

	private static BoardInvalidListRequestException invalid() {
		return new BoardInvalidListRequestException("cursor가 올바르지 않습니다.");
	}
}
