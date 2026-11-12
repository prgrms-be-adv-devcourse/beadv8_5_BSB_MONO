package com.bukang.board.app;

import org.springframework.stereotype.Component;

import com.bukang.board.domain.BoardPost;
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
}
