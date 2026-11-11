package com.bukang.board.in;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.bukang.common.global.rsdata.RsData;

/**
 * 게시판 서비스 전용 요청 예외 처리 (알림 서비스의 NotificationExceptionHandler와 같은 방식)
 * 처리하지 않으면 UnexpectedExceptionResolver가 500으로 응답한다
 */
@RestControllerAdvice
public class BoardExceptionHandler {
	// 경로 변수의 숫자 형식 오류 (예: 글 ID에 문자)
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<RsData<Void>> methodArgumentTypeMismatchException(
		MethodArgumentTypeMismatchException exception
	) {
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(RsData.of(HttpStatus.BAD_REQUEST, "요청 값의 형식이 올바르지 않습니다.", null));
	}
}
