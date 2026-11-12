package com.bukang.board.domain;

import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicUpdate;

import com.bukang.common.global.jpa.entity.BaseIdAndTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 댓글 또는 답글. parentId가 있으면 그 댓글의 답글이다 (한 단계만, 답글의 답글은 없다)
 */
// 바뀐 컬럼만 UPDATE한다. 고치는 요청이 그 사이에 신고로 숨겨진 상태를 옛 값으로 되돌리지 않게
@DynamicUpdate
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(name = "BOARD_COMMENT", indexes = @Index(name = "idx_board_comment_post", columnList = "post_id, id"))
public class BoardComment extends BaseIdAndTime {
	public static final int CONTENT_MAX = 500;

	@Column(name = "post_id", nullable = false)
	private long postId;

	@Column(name = "parent_id")
	private Long parentId;

	@Column(name = "member_id", nullable = false)
	private long memberId;

	@Column(nullable = false, length = CONTENT_MAX)
	private String content;

	@Enumerated(STRING)
	@Column(nullable = false, columnDefinition = "varchar(20)")
	private BoardCommentStatus status;

	// 고친 적이 있으면 true. 화면에 「수정됨」을 붙인다
	@Column(nullable = false)
	private boolean edited;

	// 관리자가 되살리거나 지운 기록. 되살린 뒤에는 handledAt 이후의 신고만 다시 숨김 기준에 센다
	@Enumerated(STRING)
	@Column(name = "handled_action", columnDefinition = "varchar(20)")
	private ReportHandledAction handledAction;

	@Column(name = "handled_at")
	private LocalDateTime handledAt;

	public static BoardComment create(long postId, Long parentId, long memberId, String content) {
		BoardComment comment = new BoardComment();
		comment.postId = postId;
		comment.parentId = parentId;
		comment.memberId = memberId;
		comment.content = content;
		comment.status = BoardCommentStatus.VISIBLE;
		return comment;
	}

	public boolean isWrittenBy(long memberId) {
		return this.memberId == memberId;
	}

	public boolean isDeleted() {
		return status == BoardCommentStatus.DELETED;
	}

	// 지우지도 숨기지도 않은 댓글
	public boolean isVisible() {
		return status == BoardCommentStatus.VISIBLE;
	}

	public boolean isHidden() {
		return status == BoardCommentStatus.HIDDEN;
	}

	public void edit(String content) {
		this.content = content;
		this.edited = true;
	}

	public void delete() {
		this.status = BoardCommentStatus.DELETED;
	}

	public void hide() {
		this.status = BoardCommentStatus.HIDDEN;
	}

	public void handle(ReportHandledAction action, LocalDateTime now) {
		this.status = action == ReportHandledAction.RESTORED ? BoardCommentStatus.VISIBLE : BoardCommentStatus.DELETED;
		this.handledAction = action;
		this.handledAt = now;
	}
}
