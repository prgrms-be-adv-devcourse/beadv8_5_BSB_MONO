package com.bukang.board.domain;

import static jakarta.persistence.EnumType.STRING;
import static lombok.AccessLevel.PROTECTED;

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
 * 게시판 글. 사진은 이 서비스가 들고 있지 않고 file-service가 이 글(refType BoardPost)에 연결해 보관한다
 * 좋아요 수는 BoardPostRepository의 update 쿼리로만 바꾼다 (읽고 더해 저장하면 동시에 누른 하나가 사라진다)
 */
// 바뀐 칼럼만 UPDATE한다. 좋아요 수·신고 수는 아래 updatable = false로 쿼리로만 바꾼다 (읽은 옛 값으로 덮어쓰지 않게)
@DynamicUpdate
@Entity
@NoArgsConstructor(access = PROTECTED)
@Getter
@Table(name = "BOARD_POST", indexes = {
	@Index(name = "idx_board_post_latest", columnList = "status, create_date, id"),
	@Index(name = "idx_board_post_popular", columnList = "status, create_date, like_count, id")
})
public class BoardPost extends BaseIdAndTime {
	public static final int TITLE_MAX = 50;
	public static final int CONTENT_MAX = 5000;

	@Column(name = "member_id", nullable = false)
	private long memberId;

	@Enumerated(STRING)
	@Column(nullable = false, columnDefinition = "varchar(20)")
	private BoardCategory category;

	@Column(nullable = false, length = TITLE_MAX)
	private String title;

	@Column(nullable = false, length = CONTENT_MAX)
	private String content;

	@Enumerated(STRING)
	@Column(nullable = false, columnDefinition = "varchar(20)")
	private BoardPostStatus status;

	// 고친 적이 있으면 true. 화면에 「수정됨」을 붙인다
	@Column(nullable = false)
	private boolean edited;

	@Column(name = "like_count", nullable = false, updatable = false)
	private long likeCount;

	@Column(name = "report_count", nullable = false, updatable = false)
	private int reportCount;

	public static BoardPost create(long memberId, BoardCategory category, String title, String content) {
		BoardPost post = new BoardPost();
		post.memberId = memberId;
		post.category = category;
		post.title = title;
		post.content = content;
		post.status = BoardPostStatus.VISIBLE;
		return post;
	}

	public boolean isWrittenBy(long memberId) {
		return this.memberId == memberId;
	}

	public boolean isHidden() {
		return status == BoardPostStatus.HIDDEN;
	}

	public boolean isDeleted() {
		return status == BoardPostStatus.DELETED;
	}

	public void edit(BoardCategory category, String title, String content) {
		this.category = category;
		this.title = title;
		this.content = content;
		this.edited = true;
	}

	public void delete() {
		this.status = BoardPostStatus.DELETED;
	}
}
