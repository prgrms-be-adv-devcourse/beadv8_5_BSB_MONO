package com.bukang.board.app;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bukang.board.domain.BoardComment;
import com.bukang.board.domain.BoardPost;
import com.bukang.board.domain.ReportReason;
import com.bukang.board.domain.ReportTargetType;
import com.bukang.board.domain.exception.BoardCommentNotFoundException;
import com.bukang.board.domain.exception.BoardPostNotFoundException;
import com.bukang.board.domain.exception.BoardSelfReportException;
import com.bukang.board.out.BoardCommentRepository;
import com.bukang.board.out.BoardReportRepository;

import lombok.RequiredArgsConstructor;

/**
 * 글·댓글 신고. 서로 다른 회원 5명이 신고하면 자동으로 숨긴다 (BUK-158)
 * 대상 행을 먼저 잠그고(PESSIMISTIC_WRITE) 신고를 넣고 세고 숨기므로, 동시에 들어온 신고가 서로의 수를 못 보고 지나치지 않는다
 * 같은 회원의 두 번째 신고는 조용히 넘긴다 (사유가 달라도 처음 신고 그대로, 같은 201 응답)
 */
@Service
@RequiredArgsConstructor
public class BoardReportUseCase {
	// docs/policy에 게시판 정책 문서가 없어 이슈 BUK-158 본문의 5명을 따른다
	static final int HIDE_THRESHOLD = 5;
	// 처리한 적이 없는 글·댓글은 이 시각 이후로 세므로 모든 신고가 센다
	private static final LocalDateTime COUNT_ALL_REPORTS_SINCE = LocalDateTime.of(2000, 1, 1, 0, 0);

	private final BoardPostFinder boardPostFinder;
	private final BoardCommentRepository boardCommentRepository;
	private final BoardReportRepository boardReportRepository;
	private final BoardWriterGuard boardWriterGuard;
	private final BoardOutboxKafkaAppendUseCase boardOutboxKafkaAppendUseCase;
	private final Clock clock;

	// 없는 글, 지운 글, 숨겨진 글은 404 (숨겨진 글이 있다는 사실이 드러나지 않게). 내 글은 400
	@Transactional
	public void reportPost(long memberId, long postId, ReportReason reason) {
		// 잠금을 가장 먼저 잡는다. MySQL 기본 격리(REPEATABLE READ)는 첫 일반 조회 시점의 데이터만 보여 주므로,
		// 다른 조회가 잠금보다 앞서면 잠금을 기다린 뒤에도 앞 요청이 방금 넣은 신고를 못 센다
		BoardPost post = boardPostFinder.findLockedVisible(postId)
			.orElseThrow(BoardPostNotFoundException::new);
		boardWriterGuard.checkNotWithdrawn(memberId);
		if (post.isWrittenBy(memberId)) {
			throw new BoardSelfReportException();
		}
		long reportCount = recordAndCountSinceHandled(ReportTargetType.POST, postId, memberId, reason,
			post.getHandledAt());
		if (reportCount >= HIDE_THRESHOLD) {
			post.hide();
			// 숨겨진 글은 검색에서 빠져야 하므로 색인 메시지를 같은 트랜잭션으로 남긴다 (BUK-179)
			boardOutboxKafkaAppendUseCase.appendPostChanged(postId);
		}
	}

	// 숨겨진 글의 댓글도 404다
	@Transactional
	public void reportComment(long memberId, long commentId, ReportReason reason) {
		BoardComment comment = boardCommentRepository.findLockedById(commentId)
			.filter(BoardComment::isVisible)
			.orElseThrow(BoardCommentNotFoundException::new);
		boardWriterGuard.checkNotWithdrawn(memberId);
		boardPostFinder.findVisible(comment.getPostId())
			.orElseThrow(BoardCommentNotFoundException::new);
		if (comment.isWrittenBy(memberId)) {
			throw new BoardSelfReportException();
		}
		long reportCount = recordAndCountSinceHandled(ReportTargetType.COMMENT, commentId, memberId, reason,
			comment.getHandledAt());
		if (reportCount >= HIDE_THRESHOLD) {
			comment.hide();
		}
	}

	// 신고를 넣고 숨김 기준에 세는 신고 수를 돌려준다 (되살린 적이 있으면 그 시각 이후의 신고만)
	private long recordAndCountSinceHandled(ReportTargetType type, long targetId, long memberId, ReportReason reason,
		LocalDateTime handledAt) {
		// 이미 신고한 회원이면 0행이라 넘어간다. 중복 키 예외를 내지 않아 500이 나지 않는다
		boardReportRepository.insertIgnore(type.name(), targetId, memberId, reason.name(), LocalDateTime.now(clock));
		return boardReportRepository.countByTargetTypeAndTargetIdAndCreateDateAfter(type, targetId,
			handledAt != null ? handledAt : COUNT_ALL_REPORTS_SINCE);
	}
}
