package com.bukang.board.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 없는 댓글이거나 지운 댓글이다 (지운 글의 댓글 포함)
public class BoardCommentNotFoundException extends BusinessException {
	public BoardCommentNotFoundException() {
		super(HttpStatus.NOT_FOUND, "BOARD_COMMENT_NOT_FOUND", "댓글을 찾을 수 없습니다.");
	}
}
