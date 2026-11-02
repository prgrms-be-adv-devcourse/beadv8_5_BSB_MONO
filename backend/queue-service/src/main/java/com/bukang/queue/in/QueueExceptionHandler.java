package com.bukang.queue.in;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.bukang.common.global.rsdata.RsData;

/**
 * 대기열 서비스 전용 요청 예외 처리
 * 처리하지 않으면 /error로 넘어가 공통 응답 형식(RsData)이 아닌 기본 에러 응답이 나간다.
 * common의 GlobalExceptionHandler와 함께 등록되므로, 나중에 그쪽에 포괄 핸들러(Exception 등)가 생겨도
 * 여기서 정한 400 응답이 먼저 쓰이도록 순서를 가장 앞에 둔다. 회원 ID 헤더 오류(BusinessException)는 여기서 받지 않는다
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class QueueExceptionHandler {
	// 필수 요청 헤더(예: 중복 요청 방지 키)가 없으면 잘못된 요청이다. 회원 ID 헤더는 common의 @LoginMemberId가 처리한다
	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<RsData<Void>> missingRequestHeaderException(MissingRequestHeaderException exception) {
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(RsData.of(HttpStatus.BAD_REQUEST, "필수 요청 헤더(%s)가 없습니다.".formatted(exception.getHeaderName()), null));
	}

	// 컨트롤러 파라미터에 직접 붙인 검증(예: 경로 변수 @Positive) 실패. 첫 번째 오류 메시지를 응답한다
	// 이런 검증이 있는 메서드는 @Valid 요청 본문 오류도 이 예외로 오므로 같은 형식으로 맞춘다
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<RsData<Void>> handlerMethodValidationException(HandlerMethodValidationException exception) {
		String message = exception.getAllErrors().stream()
			.findFirst()
			.map(MessageSourceResolvable::getDefaultMessage)
			.orElse("요청 값이 올바르지 않습니다.");
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(RsData.of(HttpStatus.BAD_REQUEST, message, null));
	}

	// 필수 쿼리 파라미터 누락. 헤더 누락처럼 이름을 넣어 어떤 값이 빠졌는지 알 수 있게 한다
	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<RsData<Void>> missingServletRequestParameterException(
		MissingServletRequestParameterException exception
	) {
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(RsData.of(HttpStatus.BAD_REQUEST,
				"필수 요청 값(%s)이 없습니다.".formatted(exception.getParameterName()), null));
	}

	// 경로 변수·헤더·쿼리 파라미터의 숫자 형식 오류 (예: 회원 ID에 문자)
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<RsData<Void>> methodArgumentTypeMismatchException(
		MethodArgumentTypeMismatchException exception
	) {
		return ResponseEntity
			.status(HttpStatus.BAD_REQUEST)
			.body(RsData.of(HttpStatus.BAD_REQUEST, "요청 값의 형식이 올바르지 않습니다.", null));
	}
}
