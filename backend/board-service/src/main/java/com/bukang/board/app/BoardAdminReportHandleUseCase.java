package com.bukang.board.app;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.board.domain.BoardComment;
import com.bukang.board.domain.BoardPost;
import com.bukang.board.domain.ReportHandledAction;
import com.bukang.board.domain.ReportTargetType;
import com.bukang.board.domain.exception.BoardReportNotFoundException;
import com.bukang.board.domain.exception.BoardReportNotHandleableException;
import com.bukang.board.out.BoardCommentRepository;
import com.bukang.board.out.BoardPostRepository;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 신고 처리: 숨겨진 글·댓글을 되살리거나 지운다. 부른 회원이 관리자인지는 회원 서비스에 묻는다
 * 처리는 숨겨진 것에만 한다. 대상 행을 잠근 뒤 상태를 확인하므로 두 관리자가 동시에 눌러도 한 번만 처리된다
 */
@Service
@RequiredArgsConstructor
public class BoardAdminReportHandleUseCase {
	private final BoardAdminGuard boardAdminGuard;
	private final BoardPostRepository boardPostRepository;
	private final BoardCommentRepository boardCommentRepository;
	private final BoardPostFileLinker boardPostFileLinker;
	private final BoardOutboxKafkaAppendUseCase boardOutboxKafkaAppendUseCase;
	private final Clock clock;

	// 숨겨지지 않은 대상, 이미 처리한 대상은 409, 없는 대상은 404
	@Transactional
	public void handle(long adminId, ReportTargetType type, long targetId, ReportHandledAction action) {
		boardAdminGuard.requireAdmin(adminId);
		LocalDateTime now = LocalDateTime.now(clock);
		if (type == ReportTargetType.POST) {
			handlePost(targetId, action, now);
			return;
		}
		BoardComment comment = boardCommentRepository.findLockedById(targetId)
			.orElseThrow(BoardReportNotFoundException::new);
		if (!comment.isHidden()) {
			throw new BoardReportNotHandleableException();
		}
		comment.handle(action, now);
	}

	private void handlePost(long postId, ReportHandledAction action, LocalDateTime now) {
		BoardPost post = boardPostRepository.findLockedById(postId).orElseThrow(BoardReportNotFoundException::new);
		if (!post.isHidden()) {
			throw new BoardReportNotHandleableException();
		}
		post.handle(action, now);
		// 되살리면 검색에 다시 나오고 지우면 빠지므로 색인 메시지를 같은 트랜잭션으로 남긴다 (BUK-179)
		boardOutboxKafkaAppendUseCase.appendPostChanged(post.getId());
		if (action == ReportHandledAction.DELETED) {
			// 관리자가 글을 지울 때도 사진 연결을 뗀다 (쓴 사람 이름으로 보낸다)
			boardPostFileLinker.releaseAll(post.getMemberId(), post);
		}
	}
}
