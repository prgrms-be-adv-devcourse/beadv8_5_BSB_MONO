package com.bukang.inquiry.in;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.bukang.common.global.rsdata.RsData;

/**
 * 문의 서비스 전용 요청 예외 처리 (주문 서비스의 OrderExceptionHandler와 같은 방식)
 * 처리하지 않으면 UnexpectedExceptionResolver가 500으로 응답한다
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class InquiryExceptionHandler {
	// 입력값 오류 코드는 common GlobalExceptionHandler와 같은 값을 쓴다
	private static final String INVALID_REQUEST = "INVALID_REQUEST";

	// 경로 변수의 숫자 형식 오류 (예: 문의 ID에 문자)
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<RsData<Void>> methodArgumentTypeMismatchException(
		MethodArgumentTypeMismatchException exception
	) {
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(RsData.of(HttpStatus.BAD_REQUEST, INVALID_REQUEST, "요청 값의 형식이 올바르지 않습니다.", null));
	}

	// 컨트롤러 파라미터에 직접 붙인 검증(예: 페이지 번호 @Min) 실패. 첫 번째 오류 메시지를 응답한다
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<RsData<Void>> handlerMethodValidationException(HandlerMethodValidationException exception) {
		String message = exception.getAllErrors().stream()
			.findFirst()
			.map(MessageSourceResolvable::getDefaultMessage)
			.orElse("요청 값이 올바르지 않습니다.");
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(RsData.of(HttpStatus.BAD_REQUEST, INVALID_REQUEST, message, null));
	}
}
