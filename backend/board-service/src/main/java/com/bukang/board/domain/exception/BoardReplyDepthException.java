package com.bukang.board.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 답글의 답글은 없다 (한 단계만)
public class BoardReplyDepthException extends BusinessException {
	public BoardReplyDepthException() {
		super(HttpStatus.BAD_REQUEST, "BOARD_REPLY_DEPTH", "답글에는 답글을 달 수 없습니다.");
	}
}
