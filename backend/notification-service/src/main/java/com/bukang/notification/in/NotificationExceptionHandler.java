package com.bukang.notification.in;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.bukang.common.global.rsdata.RsData;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 알림 서비스 전용 요청 예외 처리 (file-service의 FileExceptionHandler와 같은 방식)
 * 처리하지 않으면 UnexpectedExceptionResolver가 500으로 응답한다
 */
@Slf4j
@RestControllerAdvice
public class NotificationExceptionHandler {
	// 경로 변수의 숫자 형식 오류 (예: 알림 ID에 문자)
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<RsData<Void>> methodArgumentTypeMismatchException(
		MethodArgumentTypeMismatchException exception,
		HttpServletRequest request
	) {
		// 응답만 하면 서버에서 원인을 찾을 수 없다. common GlobalExceptionHandler.logBadRequest와 같은 형식으로 한 줄 남긴다
		log.info("400 {} {} message=요청 값의 형식이 올바르지 않습니다.", request.getMethod(),
			request.getRequestURI());
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(RsData.of(HttpStatus.BAD_REQUEST, "요청 값의 형식이 올바르지 않습니다.", null));
	}
}
