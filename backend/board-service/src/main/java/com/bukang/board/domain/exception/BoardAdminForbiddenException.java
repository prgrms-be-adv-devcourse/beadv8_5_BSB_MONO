package com.bukang.board.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 관리자가 아닌 회원이 관리자 API를 불렀다
public class BoardAdminForbiddenException extends BusinessException {
	public BoardAdminForbiddenException() {
		super(HttpStatus.FORBIDDEN, "BOARD_ADMIN_FORBIDDEN", "관리자만 할 수 있습니다.");
	}
}
