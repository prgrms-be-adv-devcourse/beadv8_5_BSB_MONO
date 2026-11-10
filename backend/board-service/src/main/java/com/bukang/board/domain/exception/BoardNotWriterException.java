package com.bukang.board.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 남의 글·댓글을 고치거나 지우려 했다
public class BoardNotWriterException extends BusinessException {
	public BoardNotWriterException() {
		super(HttpStatus.FORBIDDEN, "BOARD_NOT_WRITER", "작성자만 할 수 있습니다.");
	}
}
