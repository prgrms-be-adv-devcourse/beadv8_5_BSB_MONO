package com.bukang.board.app;

import java.time.Clock;
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
import com.bukang.board.out.search.BoardPostSearchPort;

import lombok.RequiredArgsConstructor;

/**
 * 글 목록·검색 (BUK-157). 로그인 없이 읽고, 지운 글과 신고로 숨겨진 글은 쓴 사람에게도 목록에 주지 않는다
 * 검색어가 있으면 Elasticsearch(글 검색 인덱스)가 맞는 글 ID를 찾고, 보이는 글인지·말머리·정렬·cursor는 DB가 그대로 맡는다 (BUK-179)
 * 그래서 인덱스에 옛 문서가 남아도 숨기거나 지운 글은 나오지 않고, 응답 모양과 cursor는 DB 검색 때와 같다
 * 검색어 없는 목록은 Elasticsearch를 부르지 않는다. 검색은 Elasticsearch가 멈추면 503이다
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
	// 검색어에 맞는 글 ID를 최신 글부터 이만큼만 가져온다. 넘게 맞는 오래된 글은 검색 결과에 나오지 않는다
	// shortcut: 한 번에 가져오는 상한(페이지로 나누지 않음). 맞는 글이 이보다 자주 넘으면 검색 인덱스에 정렬·cursor를 넣는다
	public static final int SEARCH_LIMIT = 1000;

	private final BoardPostRepository boardPostRepository;
	private final BoardCommentRepository boardCommentRepository;
	private final BoardMemberRepository boardMemberRepository;
	private final BoardPostSearchPort boardPostSearchPort;
	private final Clock clock;

	public record Page(List<PostSummaryDto> items, String nextCursor) {
	}

	public Page list(BoardCategory category, PostSort sort, String query, String cursor, Integer size) {
		int pageSize = size == null ? DEFAULT_SIZE : size;
		if (pageSize < 1 || pageSize > MAX_SIZE) {
			throw new BoardInvalidListRequestException("size는 1부터 " + MAX_SIZE + " 사이여야 합니다.");
		}
		String keyword = keywordOf(query);
		boolean searching = !keyword.isEmpty();
		// 검색어가 없으면 아래 DB 쿼리가 이 값을 보지 않는다 (쿼리의 IN에 빈 목록을 넘기지 않으려고 자리만 채운다)
		List<Long> matchedIds = searching ? boardPostSearchPort.search(keyword, SEARCH_LIMIT) : List.of(0L);
		if (searching && matchedIds.isEmpty()) {
			return new Page(List.of(), null);
		}
		List<BoardCategory> categories = category == null ? List.of(BoardCategory.values()) : List.of(category);
		PageRequest limit = PageRequest.of(0, pageSize + 1);

		PostCursor after = PostCursor.parse(sort, cursor);
		List<BoardPost> found;
		if (sort == PostSort.POPULAR) {
			found = boardPostRepository.findPopular(categories, searching, matchedIds,
				LocalDateTime.now(clock).minusDays(POPULAR_DAYS), after.likeCount(), after.postId(), limit);
		} else {
			found = boardPostRepository.findLatest(categories, searching, matchedIds, after.postId(), limit);
		}

		boolean hasNext = found.size() > pageSize;
		List<BoardPost> posts = hasNext ? found.subList(0, pageSize) : found;
		String nextCursor = null;
		if (hasNext) {
			nextCursor = PostCursor.of(posts.getLast()).format(sort);
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
		Map<Long, BoardMember> writers = boardMemberRepository
			.writersOf(posts.stream().map(BoardPost::getMemberId).toList());
		return posts.stream()
			.map(post -> PostSummaryDto.of(post, WriterDto.of(writers.get(post.getMemberId())),
				commentCounts.getOrDefault(post.getId(), 0L)))
			.toList();
	}

	// 앞뒤 공백을 뗀 검색어. 비면 검색하지 않고 전부 보여 준다
	private String keywordOf(String query) {
		String trimmed = query == null ? "" : query.trim();
		if (trimmed.length() > QUERY_MAX) {
			throw new BoardInvalidListRequestException("검색어는 " + QUERY_MAX + "자 이하로 입력해주세요.");
		}
		return trimmed;
	}
}
