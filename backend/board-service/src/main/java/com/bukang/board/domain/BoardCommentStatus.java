package com.bukang.board.domain;

/**
 * 댓글 상태. 지우기는 행을 지우지 않고 DELETED로 바꾼다 (소프트 삭제)
 */
public enum BoardCommentStatus {
	VISIBLE,
	DELETED
}
