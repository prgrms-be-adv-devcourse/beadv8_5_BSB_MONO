package com.bukang.board.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 없는 글·댓글이거나 알 수 없는 처리 주소다
public class BoardReportNotFoundException extends BusinessException {
	public BoardReportNotFoundException() {
		super(HttpStatus.NOT_FOUND, "BOARD_REPORT_NOT_FOUND", "처리할 대상을 찾을 수 없습니다.");
	}
}
