package com.bukang.board.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 숨겨지지 않았거나 이미 처리한 글·댓글이다
public class BoardReportNotHandleableException extends BusinessException {
	public BoardReportNotHandleableException() {
		super(HttpStatus.CONFLICT, "BOARD_REPORT_NOT_HANDLEABLE", "숨겨진 글·댓글만 처리할 수 있습니다.");
	}
}
