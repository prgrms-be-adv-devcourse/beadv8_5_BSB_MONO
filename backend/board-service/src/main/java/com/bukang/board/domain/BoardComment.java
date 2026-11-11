package com.bukang.board.domain;

import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

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

	public void edit(String content) {
		this.content = content;
		this.edited = true;
	}

	public void delete() {
		this.status = BoardCommentStatus.DELETED;
	}
}
