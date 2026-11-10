package com.bukang.board.app;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.board.domain.BoardComment;
import com.bukang.board.domain.BoardPost;
import com.bukang.board.domain.exception.BoardCommentNotFoundException;
import com.bukang.board.domain.exception.BoardNotWriterException;
import com.bukang.board.domain.exception.BoardPostHiddenException;
import com.bukang.board.domain.exception.BoardReplyDepthException;
import com.bukang.board.domain.exception.BoardTooFastException;
import com.bukang.board.dto.CommentSaveRequestDto;
import com.bukang.board.out.BoardCommentRepository;
import com.bukang.board.out.BoardCooldown;
import com.bukang.board.out.BoardPostRepository;

import lombok.RequiredArgsConstructor;

/**
 * 댓글·답글 쓰기, 고치기, 지우기. 지우기는 상태만 바꾼다 (소프트 삭제)
 */
@Service
@RequiredArgsConstructor
public class BoardCommentUseCase {
	private final BoardCommentRepository boardCommentRepository;
	private final BoardPostRepository boardPostRepository;
	private final BoardPostFinder boardPostFinder;
	private final BoardCooldown boardCooldown;
	private final BoardWriterGuard boardWriterGuard;

	// 숨겨진 글(쓴 사람만 볼 수 있다)에는 쓴 사람도 댓글·답글을 못 단다 (409)
	@Transactional
	public long create(long memberId, long postId, CommentSaveRequestDto request) {
		boardWriterGuard.checkNotWithdrawn(memberId);
		BoardPost post = boardPostFinder.findViewable(postId, memberId);
		if (post.isHidden()) {
			throw new BoardPostHiddenException();
		}
		if (request.parentId() != null) {
			checkParent(postId, request.parentId());
		}
		if (!boardCooldown.acquireComment(memberId)) {
			throw new BoardTooFastException();
		}
		return boardCommentRepository
			.save(BoardComment.create(postId, request.parentId(), memberId, request.content())).getId();
	}

	@Transactional
	public void update(long memberId, long commentId, CommentSaveRequestDto request) {
		boardWriterGuard.checkNotWithdrawn(memberId);
		findWritable(memberId, commentId).edit(request.content());
	}

	@Transactional
	public void delete(long memberId, long commentId) {
		boardWriterGuard.checkNotWithdrawn(memberId);
		findWritable(memberId, commentId).delete();
	}

	// 부모는 같은 글의 지우지 않은 댓글이어야 한다. 답글을 부모로 지정하면 두 단계가 되므로 막는다
	private void checkParent(long postId, long parentId) {
		BoardComment parent = boardCommentRepository.findById(parentId)
			.filter(comment -> comment.getPostId() == postId && !comment.isDeleted())
			.orElseThrow(BoardCommentNotFoundException::new);
		if (parent.getParentId() != null) {
			throw new BoardReplyDepthException();
		}
	}

	// 없는 댓글, 지운 댓글, 지운 글의 댓글은 404. 남의 댓글은 403
	// 숨겨진 글의 댓글: 글쓴이가 아니면 404(숨겨진 글이 있다는 사실을 드러내지 않음), 글쓴이면 409
	private BoardComment findWritable(long memberId, long commentId) {
		BoardComment comment = boardCommentRepository.findById(commentId)
			.filter(found -> !found.isDeleted())
			.orElseThrow(BoardCommentNotFoundException::new);
		BoardPost post = boardPostRepository.findById(comment.getPostId())
			.filter(found -> !found.isDeleted())
			.orElseThrow(BoardCommentNotFoundException::new);
		if (post.isHidden()) {
			throw post.isWrittenBy(memberId) ? new BoardPostHiddenException() : new BoardCommentNotFoundException();
		}
		if (!comment.isWrittenBy(memberId)) {
			throw new BoardNotWriterException();
		}
		return comment;
	}
}
