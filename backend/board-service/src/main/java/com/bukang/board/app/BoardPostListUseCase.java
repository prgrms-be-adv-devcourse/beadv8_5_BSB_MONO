package com.bukang.board.app;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.board.domain.BoardCategory;
import com.bukang.board.domain.BoardMember;
import com.bukang.board.domain.BoardPost;
import com.bukang.board.domain.PostSort;
import com.bukang.board.domain.exception.BoardInvalidListRequestException;
import com.bukang.board.dto.PostSummaryDto;
import com.bukang.board.dto.WriterDto;
import com.bukang.board.out.BoardCommentRepository;
import com.bukang.board.out.BoardMemberRepository;
import com.bukang.board.out.BoardPostRepository;

import lombok.RequiredArgsConstructor;

/**
 * 글 목록·검색 (BUK-157). 로그인 없이 읽고, 지운 글과 신고로 숨겨진 글은 쓴 사람에게도 목록에 주지 않는다
 * 검색은 제목·본문에 검색어가 들어 있는지(DB LIKE)로 찾는다. Elasticsearch 색인은 후속 이슈다
 * cursor는 앞 쪽 마지막 글에서 만든 값이다. 최신순은 글 ID, 인기순은 "좋아요 수-글 ID"
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BoardPostListUseCase {
	static final int DEFAULT_SIZE = 20;
	static final int MAX_SIZE = 50;
	static final int QUERY_MAX = 50;
	static final int POPULAR_DAYS = 7;

	private final BoardPostRepository boardPostRepository;
	private final BoardCommentRepository boardCommentRepository;
	private final BoardMemberRepository boardMemberRepository;

	public record Page(List<PostSummaryDto> items, String nextCursor) {
	}

	public Page list(BoardCategory category, PostSort sort, String query, String cursor, Integer size) {
		int pageSize = size == null ? DEFAULT_SIZE : size;
		if (pageSize < 1 || pageSize > MAX_SIZE) {
			throw new BoardInvalidListRequestException("size는 1부터 " + MAX_SIZE + " 사이여야 합니다.");
		}
		String pattern = likePattern(query);
		List<BoardCategory> categories = category == null ? List.of(BoardCategory.values()) : List.of(category);
		PageRequest limit = PageRequest.of(0, pageSize + 1);

		List<BoardPost> found;
		if (sort == PostSort.POPULAR) {
			long[] after = parsePopularCursor(cursor);
			found = boardPostRepository.findPopular(categories, pattern,
				LocalDateTime.now().minusDays(POPULAR_DAYS), after[0], after[1], limit);
		} else {
			found = boardPostRepository.findLatest(categories, pattern, parseLatestCursor(cursor), limit);
		}

		boolean hasNext = found.size() > pageSize;
		List<BoardPost> posts = hasNext ? found.subList(0, pageSize) : found;
		String nextCursor = null;
		if (hasNext) {
			BoardPost last = posts.getLast();
			nextCursor = sort == PostSort.POPULAR ? last.getLikeCount() + "-" + last.getId() : "" + last.getId();
		}
		return new Page(toDtos(posts), nextCursor);
	}

	private List<PostSummaryDto> toDtos(List<BoardPost> posts) {
		List<Long> postIds = posts.stream().map(BoardPost::getId).toList();
		if (postIds.isEmpty()) {
			return List.of();
		}
		Map<Long, Long> commentCounts = new HashMap<>();
		for (Object[] row : boardCommentRepository.countVisibleByPostIds(postIds)) {
			commentCounts.put((Long)row[0], (Long)row[1]);
		}
		Map<Long, BoardMember> writers = new HashMap<>();
		boardMemberRepository.findAllById(posts.stream().map(BoardPost::getMemberId).distinct().toList())
			.forEach(member -> writers.put(member.getId(), member));
		return posts.stream()
			.map(post -> PostSummaryDto.of(post, WriterDto.of(writers.get(post.getMemberId())),
				commentCounts.getOrDefault(post.getId(), 0L)))
			.toList();
	}

	// 검색어가 비면 전부 찾는다. %·_는 글자 그대로 찾도록 이스케이프(!)한다
	private String likePattern(String query) {
		String trimmed = query == null ? "" : query.trim();
		if (trimmed.length() > QUERY_MAX) {
			throw new BoardInvalidListRequestException("검색어는 " + QUERY_MAX + "자 이하로 입력해주세요.");
		}
		if (trimmed.isEmpty()) {
			return "%";
		}
		return "%" + trimmed.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
	}

	private long parseLatestCursor(String cursor) {
		if (isFirstPage(cursor)) {
			return Long.MAX_VALUE;
		}
		return parsePositive(cursor);
	}

	// [좋아요 수, 글 ID]. 처음 쪽은 [MAX, MAX]라 모든 글이 뒤에 온다
	private long[] parsePopularCursor(String cursor) {
		if (isFirstPage(cursor)) {
			return new long[] {Long.MAX_VALUE, Long.MAX_VALUE};
		}
		String[] parts = cursor.split("-", -1);
		if (parts.length != 2) {
			throw invalidCursor();
		}
		long likes = parseNumber(parts[0]);
		return new long[] {likes, parsePositive(parts[1])};
	}

	// cursor를 비워서(cursor=) 보내도 첫 쪽이다
	private boolean isFirstPage(String cursor) {
		return cursor == null || cursor.isBlank();
	}

	private long parsePositive(String text) {
		long value = parseNumber(text);
		if (value < 1) {
			throw invalidCursor();
		}
		return value;
	}

	private long parseNumber(String text) {
		try {
			long value = Long.parseLong(text);
			if (value < 0) {
				throw invalidCursor();
			}
			return value;
		} catch (NumberFormatException exception) {
			throw invalidCursor();
		}
	}

	private BoardInvalidListRequestException invalidCursor() {
		return new BoardInvalidListRequestException("cursor가 올바르지 않습니다.");
	}
}
