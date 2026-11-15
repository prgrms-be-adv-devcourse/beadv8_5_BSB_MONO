package com.bukang.board.domain.exception;

import org.springframework.http.HttpStatus;

import com.bukang.common.global.exception.BusinessException;

// 회원 서비스에 연결하지 못해 관리자인지 알 수 없다. 모르는 사람을 관리자로 보지 않고 다시 시도하게 한다
public class BoardExternalServiceUnavailableException extends BusinessException {
	public BoardExternalServiceUnavailableException(String message) {
		super(HttpStatus.SERVICE_UNAVAILABLE, "BOARD_EXTERNAL_SERVICE_UNAVAILABLE", message);
	}
}
