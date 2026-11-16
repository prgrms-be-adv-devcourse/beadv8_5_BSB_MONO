package com.bukang.board.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 목록 요청의 cursor·size·검색어가 올바르지 않다
public class BoardInvalidListRequestException extends BusinessException {
	public BoardInvalidListRequestException(String message) {
		super(HttpStatus.BAD_REQUEST, "BOARD_INVALID_LIST_REQUEST", message);
	}
}
