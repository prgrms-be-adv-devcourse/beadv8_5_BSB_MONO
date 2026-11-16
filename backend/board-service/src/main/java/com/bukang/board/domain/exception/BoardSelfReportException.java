package com.bukang.board.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 내 글이나 내 댓글을 신고하려 했다
public class BoardSelfReportException extends BusinessException {
	public BoardSelfReportException() {
		super(HttpStatus.BAD_REQUEST, "BOARD_SELF_REPORT", "내 글이나 내 댓글은 신고할 수 없습니다.");
	}
}
