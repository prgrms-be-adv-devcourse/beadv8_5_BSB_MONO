package com.bukang.board.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 신고로 숨겨진 내 글을 고치거나 거기에 댓글·답글을 달려 했다 (관리자가 원래 내용을 보고 판단하도록 막는다)
public class BoardPostHiddenException extends BusinessException {
	public BoardPostHiddenException() {
		super(HttpStatus.CONFLICT, "BOARD_POST_HIDDEN", "신고로 숨겨진 글은 고치거나 댓글을 달 수 없습니다. 지우기만 할 수 있습니다.");
	}
}
