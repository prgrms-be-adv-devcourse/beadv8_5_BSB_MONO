package com.bukang.board.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 도배 제한(글 1분, 댓글 10초)에 걸렸다
public class BoardTooFastException extends BusinessException {
	public BoardTooFastException() {
		super(HttpStatus.TOO_MANY_REQUESTS, "BOARD_TOO_FAST", "작성 간격이 너무 짧습니다. 잠시 뒤에 다시 시도해주세요.");
	}
}
