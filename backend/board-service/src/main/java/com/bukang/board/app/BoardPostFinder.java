package com.bukang.board.app;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.bukang.board.domain.BoardPost;
import com.bukang.board.domain.exception.BoardNotWriterException;
import com.bukang.board.domain.exception.BoardPostNotFoundException;
import com.bukang.board.out.BoardPostRepository;

import lombok.RequiredArgsConstructor;

/**
 * 요청한 사람이 볼 수 있는 글을 찾는다. 없는 글, 지운 글, 남의 숨겨진 글은 모두 같은 404다
 * (숨겨진 글이 있다는 사실이 드러나지 않게). 숨겨진 글은 쓴 사람에게만 보인다
 */
@Component
@RequiredArgsConstructor
public class BoardPostFinder {
	private final BoardPostRepository boardPostRepository;

	// viewerId: 로그인 안 했으면 null
	public BoardPost findViewable(long postId, Long viewerId) {
		BoardPost post = boardPostRepository.findById(postId).orElseThrow(BoardPostNotFoundException::new);
		if (post.isDeleted()) {
			throw new BoardPostNotFoundException();
		}
		if (post.isHidden() && (viewerId == null || !post.isWrittenBy(viewerId))) {
			throw new BoardPostNotFoundException();
		}
		return post;
	}

	// 보이는 글만. 없는 글·숨긴 글·지운 글은 빈 값이다 (호출한 쪽이 맞는 예외를 정한다)
	public Optional<BoardPost> findVisible(long postId) {
		return boardPostRepository.findById(postId).filter(BoardPost::isVisible);
	}

	// 대상 글 행을 잠그고 읽는다. 신고처럼 센 뒤 바꾸는 일은 다른 조회보다 먼저 이것을 불러야 한다
	public Optional<BoardPost> findLockedVisible(long postId) {
		return boardPostRepository.findLockedById(postId).filter(BoardPost::isVisible);
	}

	// 내 글만. 남의 글은 403
	public BoardPost findOwned(long postId, long memberId) {
		BoardPost post = findViewable(postId, memberId);
		if (!post.isWrittenBy(memberId)) {
			throw new BoardNotWriterException();
		}
		return post;
	}
}
